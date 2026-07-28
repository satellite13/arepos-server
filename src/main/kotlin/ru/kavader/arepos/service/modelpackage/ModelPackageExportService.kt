package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.dto.modelpackage.ModelPackageManifest
import ru.kavader.arepos.dto.modelpackage.ModelPackageSource
import ru.kavader.arepos.dto.modelpackage.PackagedDiagram
import ru.kavader.arepos.dto.modelpackage.PackagedDocumentRef
import ru.kavader.arepos.dto.modelpackage.PackagedFileMeta
import ru.kavader.arepos.dto.modelpackage.PackagedLink
import ru.kavader.arepos.dto.modelpackage.PackagedModel
import ru.kavader.arepos.dto.modelpackage.PackagedNode
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.FileStorageService
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class ModelPackageExportService(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val documentRefsRepository: DocumentRefsRepository,
    private val notationPackageAssembler: NotationPackageAssembler,
    private val accessService: ResourceAccessService,
    private val fileStorageServiceProvider: ObjectProvider<FileStorageService>,
    private val objectMapper: ObjectMapper
) {
    private val mdFileLinkRewriter = MdFileLinkRewriter(objectMapper)

    @Transactional(readOnly = true)
    fun export(modelId: UUID): ByteArray {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)

        val nodes = nodesRepository.findByModelIdOrdered(model.id!!, Pageable.unpaged()).content
        val links = linksRepository.findByModelOrderByIdAsc(model, Pageable.unpaged()).content
        val diagrams = diagramsRepository.findAllActiveByModelId(model.id!!)

        enforceCountLimits(nodes, links, diagrams)

        val notationIds = diagrams.mapNotNull { it.notation?.id }.toCollection(linkedSetOf())
        if (notationIds.size > ModelPackageLimits.MAX_NOTATIONS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds notation limit of ${ModelPackageLimits.MAX_NOTATIONS}"
            )
        }

        val notations = linkedMapOf<UUID, Notations>()
        for (notationId in notationIds) {
            val notation = notationsRepository.findById(notationId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $notationId not found")
            }
            notations[notationId] = notation
        }
        // Package export requires direct notation read (owner/share), not diagram-mediated view.
        val notationView = accessService.canViewNotations(notations.values)
        for (notationId in notationIds) {
            if (notationView[notationId] != true) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Notation $notationId is not readable")
            }
        }

        val notationRequests = linkedMapOf<UUID, NotationImportRequest>()
        for ((notationId, notation) in notations) {
            notationRequests[notationId] = notationPackageAssembler.toImportRequest(notation)
        }

        validateTypeCoverage(nodes, links, notationRequests.values)

        val componentsByNotation = linkedMapOf<UUID, List<Components>>()
        val relationsByNotation = linkedMapOf<UUID, List<Relations>>()
        for ((notationId, notation) in notations) {
            componentsByNotation[notationId] = componentsRepository.findByNotation(notation, Pageable.unpaged()).content
            relationsByNotation[notationId] = relationsRepository.findByNotation(notation, Pageable.unpaged()).content
        }

        val documentRefs = collectDocumentRefs(
            model = model,
            nodes = nodes,
            diagrams = diagrams,
            notationIds = notationIds,
            componentsByNotation = componentsByNotation,
            relationsByNotation = relationsByNotation
        )

        val fileBlobs = collectFileClosure(
            model = model,
            nodes = nodes,
            diagrams = diagrams,
            notations = notations.values,
            componentsByNotation = componentsByNotation,
            relationsByNotation = relationsByNotation,
            documentRefs = documentRefs
        )
        val fileIds = fileBlobs.keys

        val packagedModel = PackagedModel(
            name = model.name,
            version = model.version,
            attrs = model.attrs,
            nodes = nodes.map { node ->
                PackagedNode(
                    id = node.id!!,
                    stableId = node.stableId,
                    name = node.name,
                    nodeTypeId = node.nodeType.id!!,
                    parentNodeId = node.parentNode?.id,
                    attrs = node.attrs
                )
            },
            links = links.map { link ->
                PackagedLink(
                    id = link.id!!,
                    stableId = link.stableId,
                    sourceId = link.source.id!!,
                    targetId = link.target.id!!,
                    linkTypeId = link.linkType.id!!,
                    attrs = link.attrs
                )
            },
            diagrams = diagrams.map { diagram ->
                PackagedDiagram(
                    id = diagram.id!!,
                    name = diagram.name,
                    version = diagram.version,
                    notationId = diagram.notation.id!!,
                    nodeId = diagram.node?.id,
                    attrs = diagram.attrs
                )
            }
        )

        val packagedRefs = documentRefs.map { ref ->
            PackagedDocumentRef(
                fileId = ref.file.id,
                modelId = ref.model?.id,
                nodeId = ref.node?.id,
                diagramId = ref.diagram?.id,
                notationId = ref.notation?.id,
                componentId = ref.component?.id,
                relationId = ref.relation?.id,
                nodeTypeId = ref.nodeType?.id,
                linkTypeId = ref.linkType?.id,
                nodeShapeId = ref.nodeShape?.id
            )
        }

        val manifest = ModelPackageManifest(
            format = ModelPackageLimits.FORMAT,
            version = ModelPackageLimits.VERSION,
            exportedAt = Instant.now(),
            source = ModelPackageSource(
                modelId = model.id!!,
                modelName = model.name,
                modelVersion = model.version
            ),
            notationIds = notationIds.toList(),
            fileIds = fileIds.toList()
        )

        val zipBytes = buildZip(
            manifest = manifest,
            packagedModel = packagedModel,
            packagedRefs = packagedRefs,
            notationRequests = notationRequests,
            fileBlobs = fileBlobs
        )
        if (zipBytes.size > ModelPackageLimits.MAX_ZIP_BYTES) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Package exceeds ${ModelPackageLimits.MAX_ZIP_BYTES} bytes limit"
            )
        }
        return zipBytes
    }

    private fun enforceCountLimits(nodes: List<Nodes>, links: List<Links>, diagrams: List<Diagrams>) {
        if (nodes.size > ModelPackageLimits.MAX_NODES) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds node limit of ${ModelPackageLimits.MAX_NODES}"
            )
        }
        if (links.size > ModelPackageLimits.MAX_LINKS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds link limit of ${ModelPackageLimits.MAX_LINKS}"
            )
        }
        if (diagrams.size > ModelPackageLimits.MAX_DIAGRAMS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds diagram limit of ${ModelPackageLimits.MAX_DIAGRAMS}"
            )
        }
    }

    private fun validateTypeCoverage(
        nodes: List<Nodes>,
        links: List<Links>,
        notationRequests: Collection<NotationImportRequest>
    ) {
        val coveredNodeTypeIds = notationRequests
            .flatMap { it.nodeTypes }
            .mapNotNull { runCatching { UUID.fromString(it.id) }.getOrNull() }
            .toSet()
        val coveredLinkTypeIds = notationRequests
            .flatMap { it.linkTypes }
            .mapNotNull { runCatching { UUID.fromString(it.id) }.getOrNull() }
            .toSet()

        val missing = linkedSetOf<UUID>()
        for (node in nodes) {
            val typeId = node.nodeType.id ?: continue
            if (isSystemRootNodeType(node.nodeType.name, node.nodeType.attrs)) continue
            if (typeId !in coveredNodeTypeIds) missing.add(typeId)
        }
        for (link in links) {
            val typeId = link.linkType.id ?: continue
            if (typeId !in coveredLinkTypeIds) missing.add(typeId)
        }
        if (missing.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Model references types not included in diagram notations: ${missing.joinToString()}"
            )
        }
    }

    /**
     * Recognizes the synthetic tree-root Directory type used by [SystemRootNodeTypeService]
     * and legacy variants that exist in older databases
     * (`{"kind":"directory","system":true}`).
     */
    private fun isSystemRootNodeType(name: String?, attrs: String?): Boolean {
        if (attrs.isNullOrBlank()) return false
        return try {
            val root = objectMapper.readTree(attrs)
            if (root.path("system").path("hiddenTreeRootType").asBoolean(false)) return true
            val legacySystemFlag = root.path("system").asBoolean(false)
            val legacyKindDirectory = root.path("kind").asText("").equals("directory", ignoreCase = true)
            if (legacySystemFlag && legacyKindDirectory) return true
            // Directory by name with any system marker (boolean or nested object)
            val nameIsDirectory = name?.equals("Directory", ignoreCase = true) == true
            val hasSystemMarker = root.path("system").asBoolean(false) || root.path("system").isObject
            nameIsDirectory && hasSystemMarker
        } catch (_: Exception) {
            false
        }
    }

    private fun collectDocumentRefs(
        model: Models,
        nodes: List<Nodes>,
        diagrams: List<Diagrams>,
        notationIds: Set<UUID>,
        componentsByNotation: Map<UUID, List<Components>>,
        relationsByNotation: Map<UUID, List<Relations>>
    ): List<DocumentRefs> {
        val refs = linkedMapOf<UUID, DocumentRefs>()
        fun addAll(items: List<DocumentRefs>) {
            for (item in items) {
                refs[item.id!!] = item
            }
        }

        addAll(documentRefsRepository.findAllByModelId(model.id!!))
        val nodeIds = nodes.mapNotNull { it.id }
        if (nodeIds.isNotEmpty()) {
            addAll(documentRefsRepository.findAllByNodeIdIn(nodeIds))
        }
        val diagramIds = diagrams.mapNotNull { it.id }
        if (diagramIds.isNotEmpty()) {
            addAll(documentRefsRepository.findAllByDiagramIdIn(diagramIds))
        }
        if (notationIds.isNotEmpty()) {
            addAll(documentRefsRepository.findAllByNotationIdIn(notationIds))
        }
        val componentIds = componentsByNotation.values.flatten().mapNotNull { it.id }
        if (componentIds.isNotEmpty()) {
            addAll(documentRefsRepository.findAllByComponentIdIn(componentIds))
        }
        val relationIds = relationsByNotation.values.flatten().mapNotNull { it.id }
        if (relationIds.isNotEmpty()) {
            addAll(documentRefsRepository.findAllByRelationIdIn(relationIds))
        }
        return refs.values.toList()
    }

    private fun collectFileClosure(
        model: Models,
        nodes: List<Nodes>,
        diagrams: List<Diagrams>,
        notations: Collection<Notations>,
        componentsByNotation: Map<UUID, List<Components>>,
        relationsByNotation: Map<UUID, List<Relations>>,
        documentRefs: List<DocumentRefs>
    ): LinkedHashMap<UUID, PackagedFileContent> {
        val seed = linkedSetOf<UUID>()
        seed.addAll(mdFileLinkRewriter.extractFromAttrsJson(model.attrs))
        for (node in nodes) {
            seed.addAll(mdFileLinkRewriter.extractFromAttrsJson(node.attrs))
        }
        for (diagram in diagrams) {
            seed.addAll(mdFileLinkRewriter.extractFromAttrsJson(diagram.attrs))
        }
        for (notation in notations) {
            seed.addAll(mdFileLinkRewriter.extractFromAttrsJson(notation.attrs))
        }
        for (component in componentsByNotation.values.flatten()) {
            seed.addAll(mdFileLinkRewriter.extractFromAttrsJson(component.attrs))
        }
        for (relation in relationsByNotation.values.flatten()) {
            seed.addAll(mdFileLinkRewriter.extractFromAttrsJson(relation.attrs))
        }
        for (ref in documentRefs) {
            seed.add(ref.file.id)
        }

        val blobs = linkedMapOf<UUID, PackagedFileContent>()
        val queue = ArrayDeque<UUID>()
        for (id in seed) {
            queue.add(id)
        }

        while (queue.isNotEmpty()) {
            val fileId = queue.removeFirst()
            if (blobs.containsKey(fileId)) continue
            if (blobs.size >= ModelPackageLimits.MAX_FILES) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Package exceeds file limit of ${ModelPackageLimits.MAX_FILES}"
                )
            }
            val loaded = readFileWithVersions(fileId)
            blobs[fileId] = loaded
            for (version in loaded.versions) {
                val text = version.content.toString(Charsets.UTF_8)
                for (linked in mdFileLinkRewriter.extractFileUuids(text)) {
                    if (!blobs.containsKey(linked)) queue.add(linked)
                }
            }
        }
        return blobs
    }

    private fun readFileWithVersions(fileId: UUID): PackagedFileContent {
        val storage = fileStorage()
        val latestPair = try {
            storage.getFile(fileId)
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "File storage failed while reading file $fileId",
                ex
            )
        } ?: throw ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Referenced file blob missing: $fileId"
        )
        val file = latestPair.first
        val latestBytes = try {
            latestPair.second.inputStream.use { it.readBytes() }
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Referenced file blob missing: $fileId",
                ex
            )
        }

        val versionInfos = try {
            storage.listVersions(fileId).sortedBy { it.versionNumber }
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "File storage failed while listing versions for file $fileId",
                ex
            )
        }

        if (versionInfos.size > ModelPackageLimits.MAX_FILE_VERSIONS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File $fileId exceeds version limit of ${ModelPackageLimits.MAX_FILE_VERSIONS}"
            )
        }

        if (versionInfos.isEmpty()) {
            return PackagedFileContent(
                file = file,
                versions = listOf(PackagedFileVersion(versionNumber = 1, content = latestBytes))
            )
        }

        val versions = versionInfos.map { info ->
            val bytes = if (info.versionNumber == versionInfos.last().versionNumber) {
                latestBytes
            } else {
                readHistoricVersionBytes(storage, fileId, info.versionNumber)
            }
            PackagedFileVersion(versionNumber = info.versionNumber, content = bytes)
        }
        return PackagedFileContent(file = file, versions = versions)
    }

    private fun readHistoricVersionBytes(
        storage: FileStorageService,
        fileId: UUID,
        versionNumber: Int
    ): ByteArray {
        val pair = try {
            storage.getFileVersion(fileId, versionNumber)
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "File storage failed while reading file $fileId version $versionNumber",
                ex
            )
        } ?: throw ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Referenced file version missing: $fileId#$versionNumber"
        )
        return try {
            pair.second.inputStream.use { it.readBytes() }
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Referenced file version missing: $fileId#$versionNumber",
                ex
            )
        }
    }

    private fun fileStorage(): FileStorageService =
        fileStorageServiceProvider.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable")

    private fun buildZip(
        manifest: ModelPackageManifest,
        packagedModel: PackagedModel,
        packagedRefs: List<PackagedDocumentRef>,
        notationRequests: Map<UUID, NotationImportRequest>,
        fileBlobs: Map<UUID, PackagedFileContent>
    ): ByteArray {
        ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                fun put(name: String, bytes: ByteArray) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }

                put("manifest.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest))
                put("model.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(packagedModel))
                put(
                    "document-refs.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(packagedRefs)
                )
                for ((notationId, request) in notationRequests) {
                    put(
                        "notations/$notationId.json",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(request)
                    )
                }
                for ((fileId, packaged) in fileBlobs) {
                    val meta = PackagedFileMeta(
                        filename = packaged.file.filename,
                        contentType = packaged.file.contentType,
                        attrs = null
                    )
                    put(
                        "files/$fileId/meta.json",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(meta)
                    )
                    val latest = packaged.versions.lastOrNull()?.content ?: ByteArray(0)
                    put("files/$fileId/blob", latest)
                    for (version in packaged.versions) {
                        put("files/$fileId/versions/${version.versionNumber}", version.content)
                    }
                }
            }
            return baos.toByteArray()
        }
    }

    private data class PackagedFileVersion(
        val versionNumber: Int,
        val content: ByteArray
    )

    private data class PackagedFileContent(
        val file: Files,
        val versions: List<PackagedFileVersion>
    )
}
