package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.dto.modelpackage.ModelPackageImportOverrides
import ru.kavader.arepos.dto.modelpackage.ModelPackageImportResponse
import ru.kavader.arepos.dto.modelpackage.ModelPackageManifest
import ru.kavader.arepos.dto.modelpackage.PackagedDocumentRef
import ru.kavader.arepos.dto.modelpackage.PackagedFileMeta
import ru.kavader.arepos.dto.modelpackage.PackagedModel
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.service.FileStorageService
import ru.kavader.arepos.service.ModelAttrsService
import ru.kavader.arepos.service.NotationImportService
import ru.kavader.arepos.service.SystemRootNodeTypeService
import ru.kavader.arepos.service.modelbatch.DiagramAttrsRemapper
import ru.kavader.arepos.util.VersionUtils
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream

@Service
class ModelPackageImportService(
    private val notationImportService: NotationImportService,
    private val notationPackageReuseResolver: NotationPackageReuseResolver,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val nodeShapesRepository: NodeShapesRepository,
    private val documentRefsRepository: DocumentRefsRepository,
    private val filesRepository: FilesRepository,
    private val systemRootNodeTypeService: SystemRootNodeTypeService,
    private val modelAttrsService: ModelAttrsService,
    private val diagramAttrsRemapper: DiagramAttrsRemapper,
    private val packageAttrsRemapper: PackageAttrsRemapper,
    private val fileStorageServiceProvider: ObjectProvider<FileStorageService>,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val SYSTEM_ROOT_NODE_NAME = "Root"
        private val logger = LoggerFactory.getLogger(ModelPackageImportService::class.java)

        fun bumpMinorVersion(version: String): String? {
            val parts = version.trim().split(".")
            if (parts.size < 2) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return "$major.${minor + 1}.0"
        }
    }

    private val mdFileLinkRewriter = MdFileLinkRewriter(objectMapper)

    @Transactional
    fun importPackage(
        zipBytes: ByteArray,
        owner: Users,
        progress: PackageImportProgressListener? = null,
        overrides: ModelPackageImportOverrides? = null
    ): ModelPackageImportResponse {
        if (zipBytes.size > ModelPackageLimits.MAX_ZIP_BYTES) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Package exceeds ${ModelPackageLimits.MAX_ZIP_BYTES} bytes limit"
            )
        }

        progress?.onProgress(PackageImportStages.VALIDATING, 5, "Validating package")
        val entries = readZipEntries(zipBytes)
        val manifest = parseManifest(entries)
        val packagedModel = applyModelOverrides(parseModel(entries), overrides)
        enforceCountLimits(packagedModel, entries)

        val notationIdMap = linkedMapOf<UUID, UUID>()
        val nodeTypeIdMap = linkedMapOf<String, UUID>()
        val linkTypeIdMap = linkedMapOf<String, UUID>()
        val componentIdMap = linkedMapOf<String, UUID>()
        val relationIdMap = linkedMapOf<String, UUID>()
        val shapeIdMap = linkedMapOf<String, UUID>()
        val reuseWarnings = mutableListOf<String>()

        val notationPaths = entries.keys
            .filter { it.startsWith("notations/") && it.endsWith(".json") && !it.contains("..") }
            .sorted()
        if (notationPaths.size > ModelPackageLimits.MAX_NOTATIONS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds notation limit of ${ModelPackageLimits.MAX_NOTATIONS}"
            )
        }
        val notationPackages = notationPaths.map { path ->
            path to parseNotationPackage(path, entries.getValue(path))
        }
        validatePackagePayload(packagedModel, notationPackages)

        if (notationPaths.isEmpty()) {
            progress?.onProgress(PackageImportStages.IMPORTING_NOTATIONS, 40, "No notations to import")
        }
        notationPackages.forEachIndexed { index, (path, request) ->
            val pct = 15 + ((index + 1) * 30) / notationPaths.size
            val label = "Importing notation ${index + 1}/${notationPaths.size}"
            progress?.onProgress(PackageImportStages.IMPORTING_NOTATIONS, pct, label)
            logger.info(
                "ModelPackageImport: {} ({}, jsonBytes={})",
                label,
                path,
                entries.getValue(path).size
            )
            val startedAt = System.nanoTime()
            val reused = notationPackageReuseResolver.tryReuse(request, owner)
            val result = if (reused != null) {
                reuseWarnings += reused.warning
                reused.response
            } else {
                notationImportService.import(request, owner)
            }
            logger.info(
                "ModelPackageImport: finished notation {}/{} ({}) in {} ms (components={}, relations={}, shapes={}, reused={})",
                index + 1,
                notationPaths.size,
                path,
                (System.nanoTime() - startedAt) / 1_000_000,
                result.componentIdMap.size,
                result.relationIdMap.size,
                result.shapeIdMap.size,
                reused != null
            )
            val sourceNotationId = parseSourceNotationId(path)
            notationIdMap[sourceNotationId] = result.notationId
            nodeTypeIdMap.putAll(result.nodeTypeIdMap)
            linkTypeIdMap.putAll(result.linkTypeIdMap)
            componentIdMap.putAll(result.componentIdMap)
            relationIdMap.putAll(result.relationIdMap)
            shapeIdMap.putAll(result.shapeIdMap)
        }

        val uploadedObjectKeys = mutableListOf<String>()
        val fileIdMap: Map<UUID, UUID>
        try {
            progress?.onProgress(PackageImportStages.IMPORTING_FILES, 55, "Importing files")
            fileIdMap = importFiles(entries, owner, uploadedObjectKeys)
            remapNotationSideDocumentFileIds(
                notationIdMap = notationIdMap,
                nodeTypeIdMap = nodeTypeIdMap,
                linkTypeIdMap = linkTypeIdMap,
                fileIdMap = fileIdMap
            )

            if (modelsRepository.existsByNameAndVersion(packagedModel.name, packagedModel.version)) {
                throw PackageImportConflictException.modelExists(
                    name = packagedModel.name,
                    version = packagedModel.version,
                    suggestedVersion = bumpMinorVersion(packagedModel.version)
                )
            }

            val now = Instant.now()
            // Folder/root Directory types are excluded from notation packages on export.
            mapUnmappedSystemDirectoryTypes(packagedModel, nodeTypeIdMap, owner, now)

            progress?.onProgress(PackageImportStages.CREATING_MODEL, 75, "Creating model graph")
            val graph = createModelGraph(
                packaged = packagedModel,
                owner = owner,
                now = now,
                notationIdMap = notationIdMap,
                nodeTypeIdMap = nodeTypeIdMap,
                linkTypeIdMap = linkTypeIdMap,
                componentIdMap = componentIdMap,
                relationIdMap = relationIdMap,
                fileIdMap = fileIdMap
            )

            progress?.onProgress(PackageImportStages.DOCUMENT_REFS, 90, "Restoring document references")
            val warnings = reuseWarnings + recreateDocumentRefs(
                entries = entries,
                owner = owner,
                now = now,
                fileIdMap = fileIdMap,
                modelIdMap = mapOf(manifest.source.modelId to graph.model.id!!),
                nodeIdMap = graph.nodeIdMap,
                diagramIdMap = graph.diagramIdMap,
                notationIdMap = notationIdMap,
                componentIdMap = componentIdMap,
                relationIdMap = relationIdMap,
                nodeTypeIdMap = nodeTypeIdMap,
                linkTypeIdMap = linkTypeIdMap,
                shapeIdMap = shapeIdMap
            )

            progress?.onProgress(PackageImportStages.DONE, 100, "Done")
            return ModelPackageImportResponse(
                modelId = graph.model.id!!,
                modelName = graph.model.name,
                modelVersion = graph.model.version,
                notationIdMap = notationIdMap,
                nodeTypeIdMap = nodeTypeIdMap,
                linkTypeIdMap = linkTypeIdMap,
                fileIdMap = fileIdMap,
                warnings = warnings
            )
        } catch (ex: Exception) {
            cleanupUploadedObjects(uploadedObjectKeys)
            throw ex
        }
    }

    private fun applyModelOverrides(
        packaged: PackagedModel,
        overrides: ModelPackageImportOverrides?
    ): PackagedModel {
        if (overrides == null) return packaged
        val name = overrides.targetModelName?.trim()?.takeIf { it.isNotEmpty() } ?: packaged.name
        val version = overrides.targetModelVersion?.trim()?.takeIf { it.isNotEmpty() } ?: packaged.version
        if (name == packaged.name && version == packaged.version) return packaged
        return packaged.copy(name = name, version = version)
    }

    private fun parseManifest(entries: Map<String, ByteArray>): ModelPackageManifest {
        val bytes = entries["manifest.json"]
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Package missing manifest.json")
        val manifest = try {
            objectMapper.readValue<ModelPackageManifest>(bytes)
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid manifest.json", ex)
        }
        if (manifest.format != ModelPackageLimits.FORMAT) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported package format '${manifest.format}'"
            )
        }
        if (manifest.version != ModelPackageLimits.VERSION) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported package version ${manifest.version}"
            )
        }
        return manifest
    }

    private fun parseModel(entries: Map<String, ByteArray>): PackagedModel {
        val bytes = entries["model.json"]
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Package missing model.json")
        return try {
            objectMapper.readValue<PackagedModel>(bytes)
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid model.json", ex)
        }
    }

    private fun parseNotationPackage(path: String, bytes: ByteArray): NotationImportRequest =
        try {
            objectMapper.readValue<NotationImportRequest>(bytes)
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notation package: $path", ex)
        }

    private fun validatePackagePayload(
        model: PackagedModel,
        notationPackages: List<Pair<String, NotationImportRequest>>
    ) {
        validateStorageSemver("model.version", model.version)
        model.diagrams.forEachIndexed { index, diagram ->
            validateStorageSemver("diagrams[$index].version", diagram.version)
        }
        notationPackages.forEach { (path, request) ->
            val notationId = path.removePrefix("notations/").removeSuffix(".json")
            val prefix = "notations[$notationId]"
            validateStorageSemver("$prefix.notation.version", request.notation.version)
            request.components.forEachIndexed { index, component ->
                component.version?.let { validateStorageSemver("$prefix.components[$index].version", it) }
            }
            request.relations.forEachIndexed { index, relation ->
                relation.version?.let { validateStorageSemver("$prefix.relations[$index].version", it) }
            }
        }
    }

    private fun validateStorageSemver(path: String, version: String) {
        if (!VersionUtils.isValidStorageSemver(version)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid package semantic version at $path: '$version'"
            )
        }
    }

    private fun enforceCountLimits(packaged: PackagedModel, entries: Map<String, ByteArray>) {
        if (packaged.nodes.size > ModelPackageLimits.MAX_NODES) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds node limit of ${ModelPackageLimits.MAX_NODES}"
            )
        }
        if (packaged.links.size > ModelPackageLimits.MAX_LINKS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds link limit of ${ModelPackageLimits.MAX_LINKS}"
            )
        }
        if (packaged.diagrams.size > ModelPackageLimits.MAX_DIAGRAMS) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds diagram limit of ${ModelPackageLimits.MAX_DIAGRAMS}"
            )
        }
        val fileCount = entries.keys.count { it.matches(Regex("""files/[^/]+/meta\.json""")) }
        if (fileCount > ModelPackageLimits.MAX_FILES) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds file limit of ${ModelPackageLimits.MAX_FILES}"
            )
        }
    }

    private fun parseSourceNotationId(path: String): UUID {
        val idPart = path.removePrefix("notations/").removeSuffix(".json")
        return try {
            UUID.fromString(idPart)
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notation entry path: $path", ex)
        }
    }

    private fun importFiles(
        entries: Map<String, ByteArray>,
        owner: Users,
        uploadedObjectKeys: MutableList<String>
    ): Map<UUID, UUID> {
        val sourceIds = entries.keys
            .mapNotNull { name ->
                val match = Regex("""^files/([^/]+)/meta\.json$""").matchEntire(name) ?: return@mapNotNull null
                try {
                    UUID.fromString(match.groupValues[1])
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            .distinct()
            .sortedBy { it.toString() }

        if (sourceIds.size > ModelPackageLimits.MAX_FILES) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package exceeds file limit of ${ModelPackageLimits.MAX_FILES}"
            )
        }

        val fileIdMap = sourceIds.associateWith { UUID.randomUUID() }.toMap()
        val storage = fileStorage()

        for (sourceId in sourceIds) {
            val metaBytes = entries["files/$sourceId/meta.json"]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing meta.json for file $sourceId")
            val meta = try {
                objectMapper.readValue<PackagedFileMeta>(metaBytes)
            } catch (ex: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid meta.json for file $sourceId", ex)
            }

            val versionPrefix = "files/$sourceId/versions/"
            val versionEntries = entries
                .mapNotNull { (name, bytes) ->
                    if (!name.startsWith(versionPrefix)) return@mapNotNull null
                    val numberPart = name.removePrefix(versionPrefix)
                    val versionNumber = numberPart.toIntOrNull()
                        ?: throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid version path for file $sourceId: $name"
                        )
                    versionNumber to bytes
                }
                .sortedBy { it.first }

            if (versionEntries.size > ModelPackageLimits.MAX_FILE_VERSIONS) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File $sourceId exceeds version limit of ${ModelPackageLimits.MAX_FILE_VERSIONS}"
                )
            }

            val versionContents = if (versionEntries.isNotEmpty()) {
                versionEntries.map { it.second }
            } else {
                val blob = entries["files/$sourceId/blob"]
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing blob for file $sourceId")
                listOf(blob)
            }

            val rewrittenVersions = versionContents.map { content ->
                if (isTextualContent(meta.contentType, meta.filename)) {
                    mdFileLinkRewriter.rewrite(content.toString(Charsets.UTF_8), fileIdMap)
                        .toByteArray(Charsets.UTF_8)
                } else {
                    content
                }
            }

            val newId = fileIdMap.getValue(sourceId)
            val firstContent = rewrittenVersions.first()
            val saved = try {
                storage.createOwnedBlob(
                    id = newId,
                    content = firstContent,
                    filename = meta.filename,
                    contentType = meta.contentType,
                    owner = owner
                )
            } catch (ex: ResponseStatusException) {
                throw ex
            } catch (ex: Exception) {
                throw ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "File storage failed while importing file $sourceId",
                    ex
                )
            }
            uploadedObjectKeys.add(saved.objectKey)

            for (i in 1 until rewrittenVersions.size) {
                try {
                    storage.appendOwnedBlobVersion(newId, rewrittenVersions[i], owner)
                } catch (ex: ResponseStatusException) {
                    throw ex
                } catch (ex: Exception) {
                    throw ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "File storage failed while importing file $sourceId version ${i + 1}",
                        ex
                    )
                }
            }
        }
        return fileIdMap
    }

    private fun isTextualContent(contentType: String, filename: String): Boolean {
        val type = contentType.lowercase()
        return type.startsWith("text/") ||
            type.contains("markdown") ||
            type.contains("json") ||
            filename.lowercase().endsWith(".md")
    }

    private fun remapNotationSideDocumentFileIds(
        notationIdMap: Map<UUID, UUID>,
        nodeTypeIdMap: Map<String, UUID>,
        linkTypeIdMap: Map<String, UUID>,
        fileIdMap: Map<UUID, UUID>
    ) {
        if (fileIdMap.isEmpty()) return

        for (newNotationId in notationIdMap.values) {
            val notation = notationsRepository.findById(newNotationId).orElse(null) ?: continue
            val remapped = packageAttrsRemapper.remapModelOrEntityAttrs(notation.attrs, fileIdMap)
            if (remapped != notation.attrs) {
                notation.attrs = remapped
                notationsRepository.save(notation)
            }
            val components = componentsRepository
                .findByNotation(notation, Pageable.unpaged())
                .content
            for (component in components) {
                val remappedComponent = packageAttrsRemapper.remapModelOrEntityAttrs(component.attrs, fileIdMap)
                if (remappedComponent != component.attrs) {
                    component.attrs = remappedComponent
                    componentsRepository.save(component)
                }
            }
            val relations = relationsRepository
                .findByNotation(notation, Pageable.unpaged())
                .content
            for (relation in relations) {
                val remappedRelation = packageAttrsRemapper.remapModelOrEntityAttrs(relation.attrs, fileIdMap)
                if (remappedRelation != relation.attrs) {
                    relation.attrs = remappedRelation
                    relationsRepository.save(relation)
                }
            }
        }

        for (typeId in nodeTypeIdMap.values.toSet()) {
            val nodeType = nodeTypesRepository.findById(typeId).orElse(null) ?: continue
            val remapped = mdFileLinkRewriter.rewriteAttrsJson(nodeType.attrs, fileIdMap)
            if (remapped != nodeType.attrs) {
                nodeType.attrs = remapped
                nodeTypesRepository.save(nodeType)
            }
        }
        for (typeId in linkTypeIdMap.values.toSet()) {
            val linkType = linkTypesRepository.findById(typeId).orElse(null) ?: continue
            val remapped = mdFileLinkRewriter.rewriteAttrsJson(linkType.attrs, fileIdMap)
            if (remapped != linkType.attrs) {
                linkType.attrs = remapped
                linkTypesRepository.save(linkType)
            }
        }
    }

    /**
     * Export omits system Directory node types from notation payloads (they are not notation
     * components). Folder nodes still reference those source type IDs — map them to the
     * importer's Directory type so createModelGraph can resolve them.
     */
    private fun mapUnmappedSystemDirectoryTypes(
        packaged: PackagedModel,
        nodeTypeIdMap: MutableMap<String, UUID>,
        owner: Users,
        now: Instant
    ) {
        val unmapped = packaged.nodes
            .map { it.nodeTypeId.toString() }
            .filter { it !in nodeTypeIdMap }
            .toSet()
        if (unmapped.isEmpty()) return

        val directoryTypeId = systemRootNodeTypeService.getOrCreate(owner, now).id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Directory type id is null")
        for (sourceTypeId in unmapped) {
            nodeTypeIdMap[sourceTypeId] = directoryTypeId
        }
    }

    private fun createModelGraph(
        packaged: PackagedModel,
        owner: Users,
        now: Instant,
        notationIdMap: Map<UUID, UUID>,
        nodeTypeIdMap: Map<String, UUID>,
        linkTypeIdMap: Map<String, UUID>,
        componentIdMap: Map<String, UUID>,
        relationIdMap: Map<String, UUID>,
        fileIdMap: Map<UUID, UUID>
    ): ImportedGraph {
        val model = modelsRepository.save(
            Models(
                name = packaged.name,
                createdAt = now,
                updatedAt = now,
                attrs = packageAttrsRemapper.remapModelOrEntityAttrs(
                    attrs = packaged.attrs,
                    fileIdMap = fileIdMap
                ),
                version = packaged.version,
                owner = owner,
                deleted = false
            )
        )

        val packageRoot = packaged.nodes.firstOrNull { it.parentNodeId == null }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Package model has no root node")

        val rootNodeType = systemRootNodeTypeService.getOrCreate(owner, now)
        val newRoot = nodesRepository.save(
            Nodes(
                stableId = packageRoot.stableId,
                name = SYSTEM_ROOT_NODE_NAME,
                createdAt = now,
                updatedAt = now,
                attrs = packageAttrsRemapper.remapModelOrEntityAttrs(
                    attrs = packageRoot.attrs,
                    fileIdMap = fileIdMap,
                    notationIdMap = notationIdMap,
                    componentIdMap = componentIdMap,
                    relationIdMap = relationIdMap
                ),
                parentNode = null,
                model = model,
                owner = owner,
                nodeType = rootNodeType
            )
        )
        model.attrs = modelAttrsService.mergeWithTreeRootNodeId(model.attrs, newRoot.id!!)
        modelsRepository.save(model)

        val nodeIdMap = linkedMapOf(packageRoot.id to newRoot.id!!)
        val nodesByNewId = linkedMapOf(newRoot.id!! to newRoot)

        val pending = packaged.nodes.filter { it.id != packageRoot.id }.toMutableList()
        while (pending.isNotEmpty()) {
            var progress = false
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val src = iterator.next()
                val parentSourceId = src.parentNodeId ?: continue
                val newParentId = nodeIdMap[parentSourceId] ?: continue
                val newParent = nodesByNewId[newParentId] ?: continue
                val mappedTypeId = nodeTypeIdMap[src.nodeTypeId.toString()]
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown nodeTypeId ${src.nodeTypeId} for node ${src.id}"
                    )
                val nodeType = loadNodeType(mappedTypeId)
                val saved = nodesRepository.save(
                    Nodes(
                        stableId = src.stableId,
                        name = src.name,
                        createdAt = now,
                        updatedAt = now,
                        attrs = packageAttrsRemapper.remapModelOrEntityAttrs(
                            attrs = src.attrs,
                            fileIdMap = fileIdMap,
                            notationIdMap = notationIdMap,
                            componentIdMap = componentIdMap,
                            relationIdMap = relationIdMap
                        ),
                        parentNode = newParent,
                        model = model,
                        owner = owner,
                        nodeType = nodeType
                    )
                )
                nodeIdMap[src.id] = saved.id!!
                nodesByNewId[saved.id!!] = saved
                iterator.remove()
                progress = true
            }
            if (!progress) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Broken node parent hierarchy in package (unmapped parents remain)"
                )
            }
        }

        val linkIdMap = linkedMapOf<UUID, UUID>()
        for (srcLink in packaged.links) {
            val newSourceId = nodeIdMap[srcLink.sourceId]
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown link source ${srcLink.sourceId}"
                )
            val newTargetId = nodeIdMap[srcLink.targetId]
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown link target ${srcLink.targetId}"
                )
            val mappedLinkTypeId = linkTypeIdMap[srcLink.linkTypeId.toString()]
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown linkTypeId ${srcLink.linkTypeId} for link ${srcLink.id}"
                )
            val saved = linksRepository.save(
                Links(
                    stableId = srcLink.stableId,
                    source = nodesByNewId.getValue(newSourceId),
                    target = nodesByNewId.getValue(newTargetId),
                    attrs = packageAttrsRemapper.remapModelOrEntityAttrs(
                        attrs = srcLink.attrs,
                        fileIdMap = fileIdMap,
                        notationIdMap = notationIdMap,
                        componentIdMap = componentIdMap,
                        relationIdMap = relationIdMap
                    ),
                    createdAt = now,
                    updatedAt = now,
                    owner = owner,
                    linkType = loadLinkType(mappedLinkTypeId),
                    model = model
                )
            )
            linkIdMap[srcLink.id] = saved.id!!
        }

        val stringNodeIdMap = nodeIdMap.mapKeys { it.key.toString() }
        val stringLinkIdMap = linkIdMap.mapKeys { it.key.toString() }
        val diagramIdMap = linkedMapOf<UUID, UUID>()

        for (srcDiagram in packaged.diagrams) {
            val newNotationId = notationIdMap[srcDiagram.notationId]
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown notationId ${srcDiagram.notationId} for diagram ${srcDiagram.id}"
                )
            val notation = notationsRepository.findById(newNotationId).orElseThrow {
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Notation $newNotationId not found")
            }
            val boundNode = srcDiagram.nodeId?.let { sourceNodeId ->
                val newNodeId = nodeIdMap[sourceNodeId]
                    ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown diagram nodeId $sourceNodeId"
                    )
                nodesByNewId.getValue(newNodeId)
            }

            var attrs = diagramAttrsRemapper.remap(srcDiagram.attrs, stringNodeIdMap, stringLinkIdMap)
            attrs = packageAttrsRemapper.remapDiagramExtras(
                attrs = attrs,
                fileIdMap = fileIdMap,
                componentIdMap = componentIdMap,
                relationIdMap = relationIdMap
            )

            val saved = diagramsRepository.save(
                Diagrams(
                    name = srcDiagram.name,
                    version = srcDiagram.version,
                    createdAt = now,
                    updatedAt = now,
                    attrs = attrs,
                    owner = owner,
                    model = model,
                    notation = notation,
                    node = boundNode,
                    deleted = false
                )
            )
            diagramIdMap[srcDiagram.id] = saved.id!!
        }

        return ImportedGraph(
            model = modelsRepository.findById(model.id!!).orElse(model),
            nodeIdMap = nodeIdMap,
            diagramIdMap = diagramIdMap
        )
    }

    private fun recreateDocumentRefs(
        entries: Map<String, ByteArray>,
        owner: Users,
        now: Instant,
        fileIdMap: Map<UUID, UUID>,
        modelIdMap: Map<UUID, UUID>,
        nodeIdMap: Map<UUID, UUID>,
        diagramIdMap: Map<UUID, UUID>,
        notationIdMap: Map<UUID, UUID>,
        componentIdMap: Map<String, UUID>,
        relationIdMap: Map<String, UUID>,
        nodeTypeIdMap: Map<String, UUID>,
        linkTypeIdMap: Map<String, UUID>,
        shapeIdMap: Map<String, UUID>
    ): List<String> {
        val warnings = mutableListOf<String>()
        val refBytes = entries["document-refs.json"] ?: return warnings
        val refs = try {
            objectMapper.readValue<List<PackagedDocumentRef>>(refBytes)
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document-refs.json", ex)
        }

        for (ref in refs) {
            val newFileId = fileIdMap[ref.fileId]
            if (newFileId == null) {
                warnings.add("Skipped document_ref: unmapped fileId ${ref.fileId}")
                continue
            }
            val file = filesRepository.findById(newFileId).orElse(null) ?: run {
                warnings.add("Skipped document_ref: file $newFileId not found")
                continue
            }

            val model = ref.modelId?.let { modelIdMap[it] }?.let { id ->
                modelsRepository.findById(id).orElse(null)
            }
            val node = ref.nodeId?.let { nodeIdMap[it] }?.let { id ->
                nodesRepository.findById(id).orElse(null)
            }
            val diagram = ref.diagramId?.let { diagramIdMap[it] }?.let { id ->
                diagramsRepository.findById(id).orElse(null)
            }
            val notation = ref.notationId?.let { notationIdMap[it] }?.let { id ->
                notationsRepository.findById(id).orElse(null)
            }
            val component = ref.componentId?.let { componentIdMap[it.toString()] }?.let { id ->
                componentsRepository.findById(id).orElse(null)
            }
            val relation = ref.relationId?.let { relationIdMap[it.toString()] }?.let { id ->
                relationsRepository.findById(id).orElse(null)
            }
            val nodeType = ref.nodeTypeId?.let { nodeTypeIdMap[it.toString()] }?.let { id ->
                nodeTypesRepository.findById(id).orElse(null)
            }
            val linkType = ref.linkTypeId?.let { linkTypeIdMap[it.toString()] }?.let { id ->
                linkTypesRepository.findById(id).orElse(null)
            }
            val nodeShape = ref.nodeShapeId?.let { shapeIdMap[it.toString()] }?.let { id ->
                nodeShapesRepository.findById(id).orElse(null)
            }

            val hasEntity = model != null || node != null || diagram != null || notation != null ||
                component != null || relation != null || nodeType != null || linkType != null || nodeShape != null
            if (!hasEntity) {
                warnings.add("Skipped document_ref for file ${ref.fileId}: no remappable entity side")
                continue
            }

            // Skip if any non-null source side failed to remap
            if (ref.modelId != null && model == null ||
                ref.nodeId != null && node == null ||
                ref.diagramId != null && diagram == null ||
                ref.notationId != null && notation == null ||
                ref.componentId != null && component == null ||
                ref.relationId != null && relation == null ||
                ref.nodeTypeId != null && nodeType == null ||
                ref.linkTypeId != null && linkType == null ||
                ref.nodeShapeId != null && nodeShape == null
            ) {
                warnings.add("Skipped document_ref for file ${ref.fileId}: partial entity remap failure")
                continue
            }

            documentRefsRepository.save(
                DocumentRefs(
                    file = file,
                    createdBy = owner,
                    createdAt = now,
                    model = model,
                    node = node,
                    diagram = diagram,
                    notation = notation,
                    component = component,
                    relation = relation,
                    nodeType = nodeType,
                    linkType = linkType,
                    nodeShape = nodeShape
                )
            )
        }
        return warnings
    }

    private fun loadNodeType(id: UUID): NodeTypes =
        nodeTypesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "NodeType $id not found")
        }

    private fun loadLinkType(id: UUID): LinkTypes =
        linkTypesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "LinkType $id not found")
        }

    private fun fileStorage(): FileStorageService =
        fileStorageServiceProvider.getIfAvailable()
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable")

    private fun cleanupUploadedObjects(objectKeys: List<String>) {
        if (objectKeys.isEmpty()) return
        val storage = fileStorageServiceProvider.getIfAvailable() ?: return
        for (key in objectKeys) {
            storage.deleteObjectQuietly(key)
        }
    }

    private fun readZipEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        if (zipBytes.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Package ZIP is empty")
        }
        val result = linkedMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsafe ZIP entry: $name")
                    }
                    if (!entry.isDirectory) {
                        result[name] = zis.readBytes()
                    }
                    zis.closeEntry()
                }
            }
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid package ZIP", ex)
        }
        if (result.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Package ZIP has no entries")
        }
        return result
    }

    private data class ImportedGraph(
        val model: Models,
        val nodeIdMap: Map<UUID, UUID>,
        val diagramIdMap: Map<UUID, UUID>
    )
}
