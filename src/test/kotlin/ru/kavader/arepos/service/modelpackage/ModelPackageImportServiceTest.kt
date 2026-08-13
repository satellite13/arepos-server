package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.RepositoryTestBase
import ru.kavader.arepos.dto.modelpackage.ModelPackageManifest
import ru.kavader.arepos.dto.modelpackage.ModelPackageSource
import ru.kavader.arepos.dto.modelpackage.PackagedDiagram
import ru.kavader.arepos.dto.modelpackage.PackagedModel
import ru.kavader.arepos.dto.modelpackage.PackagedNode
import ru.kavader.arepos.service.FileStorageService
import ru.kavader.arepos.service.SystemRootNodeTypeService
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(ModelPackageImportServiceTest.FileStorageTestConfiguration::class)
class ModelPackageImportServiceTest : RepositoryTestBase() {

    @Autowired
    lateinit var exportService: ModelPackageExportService

    @Autowired
    lateinit var importService: ModelPackageImportService

    @Autowired
    lateinit var filesRepository: FilesRepository

    @Autowired
    lateinit var documentRefsRepository: DocumentRefsRepository

    @Autowired
    lateinit var fileStorageService: FileStorageService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var systemRootNodeTypeService: SystemRootNodeTypeService

    @AfterEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
        FileStorageTestConfiguration.blobs.clear()
        FileStorageTestConfiguration.versionBlobs.clear()
    }

    @Test
    fun `round trip preserves graph wiki links and document refs`() {
        val ownerA = persistUser(email = "package-import-a@test.com")
        authAs(ownerA.id!!, Role.USER)

        val secondFileId = UUID.randomUUID()
        val firstFileId = UUID.randomUUID()
        val secondContent = "Second wiki page"
        val firstContent = "See [second](mdfile://$secondFileId)"

        val notation = persistNotation(owner = ownerA, name = "Import Round Notation", version = "1.0.0")
        // documentFileId on types is remapped after import (file also referenced from model so export includes it)
        val nodeType = persistNodeType(
            owner = ownerA,
            name = "Import Round Type",
            attrs = """{"documentFileId":"$firstFileId","color":"#00aa00"}"""
        )
        val linkType = persistLinkType(
            owner = ownerA,
            name = "Import Round Link Type",
            attrs = """{"documentFileId":"$firstFileId","directional":true}"""
        )
        val component = persistComponent(
            notation = notation,
            nodeType = nodeType,
            owner = ownerA,
            name = "Import Round Component"
        )
        persistRelation(
            notation = notation,
            linkType = linkType,
            owner = ownerA,
            name = "Import Round Relation"
        )

        val secondFile = persistWikiFile(ownerA, secondFileId, "second.md", secondContent)
        val firstFile = persistWikiFile(ownerA, firstFileId, "first.md", firstContent)
        stubFileBlob(secondFile, secondContent)
        stubFileBlob(firstFile, firstContent)

        val model = persistModel(
            owner = ownerA,
            name = "Import Round Model",
            version = "1.0.0",
            attrs = """{"documentFileId":"$firstFileId"}"""
        )
        val root = persistNode(
            model = model,
            owner = ownerA,
            nodeType = nodeType,
            name = "Root",
            attrs = """{"system":{"hiddenTreeRoot":true},"treeOrder":0}"""
        )
        val child = persistNode(
            model = model,
            owner = ownerA,
            nodeType = nodeType,
            name = "Business Actor",
            parent = root,
            attrs = """{"label":"actor","notationComponents":{"${notation.id}":{"componentId":"${component.id}"}}}"""
        )
        persistDiagram(
            model = model,
            notation = notation,
            owner = ownerA,
            node = child,
            name = "Main",
            version = "1.0.0",
            attrs = """{"instances":{"nodes":[{"id":"n1","modelNodeId":"${child.id}","x":10,"y":20}],"edges":[]}}"""
        )
        documentRefsRepository.save(
            DocumentRefs(
                file = firstFile,
                createdBy = ownerA,
                createdAt = Instant.now(),
                model = model
            )
        )
        documentRefsRepository.save(
            DocumentRefs(
                file = secondFile,
                createdBy = ownerA,
                createdAt = Instant.now(),
                node = child
            )
        )

        val zipBytes = exportService.export(model.id!!)

        // Free global name+version keys so import as another user can succeed.
        notation.name = "Import Round Notation Archived"
        notationsRepository.save(notation)
        model.name = "Import Round Model Archived"
        modelsRepository.save(model)

        val ownerB = persistUser(email = "package-import-b@test.com")
        authAs(ownerB.id!!, Role.USER)

        val response = importService.importPackage(zipBytes, ownerB)

        assertNotEquals(model.id, response.modelId)
        assertEquals("Import Round Model", response.modelName)
        assertEquals("1.0.0", response.modelVersion)
        assertTrue(response.notationIdMap.containsKey(notation.id))
        assertTrue(response.fileIdMap.containsKey(firstFileId))
        assertTrue(response.fileIdMap.containsKey(secondFileId))

        val importedModel = modelsRepository.findById(response.modelId).orElseThrow()
        assertEquals(ownerB.id, importedModel.owner.id)

        val importedNodes = nodesRepository.findByModelIdOrdered(response.modelId, Pageable.unpaged()).content
        assertEquals(2, importedNodes.size)
        assertTrue(importedNodes.any { it.parentNode == null && it.name == "Root" })
        assertTrue(importedNodes.any { it.parentNode != null && it.name == "Business Actor" })

        val importedDiagrams = diagramsRepository.findAllActiveByModelId(response.modelId)
        assertEquals(1, importedDiagrams.size)
        val importedDiagram = importedDiagrams.first()
        assertEquals(response.notationIdMap[notation.id], importedDiagram.notation.id)
        assertTrue(importedDiagram.attrs!!.contains(importedNodes.first { it.name == "Business Actor" }.id.toString()))

        val newFirstId = response.fileIdMap.getValue(firstFileId)
        val newSecondId = response.fileIdMap.getValue(secondFileId)
        assertTrue(importedModel.attrs!!.contains(newFirstId.toString()))
        assertTrue(!importedModel.attrs!!.contains(firstFileId.toString()))

        val rewritten = FileStorageTestConfiguration.blobs[newFirstId]?.toString(Charsets.UTF_8)
        assertNotNull(rewritten)
        assertTrue(rewritten.contains("mdfile://$newSecondId"))
        assertTrue(!rewritten.contains("mdfile://$secondFileId"))

        val importedNodeTypeId = response.nodeTypeIdMap.values.first()
        val importedNodeType = nodeTypesRepository.findById(importedNodeTypeId).orElseThrow()
        assertTrue(importedNodeType.attrs!!.contains(newFirstId.toString()))
        assertTrue(!importedNodeType.attrs!!.contains(firstFileId.toString()))

        val importedLinkTypeId = response.linkTypeIdMap.values.first()
        val importedLinkType = linkTypesRepository.findById(importedLinkTypeId).orElseThrow()
        assertTrue(importedLinkType.attrs!!.contains(newFirstId.toString()))
        assertTrue(!importedLinkType.attrs!!.contains(firstFileId.toString()))

        val modelRefs = documentRefsRepository.findAllByModelId(response.modelId)
        assertEquals(1, modelRefs.size)
        assertEquals(newFirstId, modelRefs.first().file.id)

        val childNode = importedNodes.first { it.name == "Business Actor" }
        val nodeRefs = documentRefsRepository.findAllByNodeIdIn(listOf(childNode.id!!))
        assertEquals(1, nodeRefs.size)
        assertEquals(newSecondId, nodeRefs.first().file.id)
    }

    @Test
    fun `round trip preserves wiki file version history`() {
        val ownerA = persistUser(email = "package-import-versions-a@test.com")
        authAs(ownerA.id!!, Role.USER)

        val fileId = UUID.randomUUID()
        val linkedId = UUID.randomUUID()
        val v1 = "Draft one"
        val v2 = "Draft two with [link](mdfile://$linkedId)"
        val v3 = "Final with [link](mdfile://$linkedId)"
        val linkedContent = "Linked page"

        val notation = persistNotation(owner = ownerA, name = "Versions Round Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = ownerA, name = "Versions Round Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = ownerA)

        val linkedFile = persistWikiFile(ownerA, linkedId, "linked.md", linkedContent)
        val historyFile = persistWikiFile(ownerA, fileId, "history.md", v3)
        stubFileBlob(linkedFile, linkedContent)
        stubFileVersions(historyFile, listOf(v1, v2, v3))

        val model = persistModel(
            owner = ownerA,
            name = "Versions Round Model",
            version = "1.0.0",
            attrs = """{"documentFileId":"$fileId"}"""
        )
        persistNode(model = model, owner = ownerA, nodeType = nodeType, name = "Root")
        persistDiagram(model = model, notation = notation, owner = ownerA, name = "Main")

        val zipBytes = exportService.export(model.id!!)

        notation.name = "Versions Round Notation Archived"
        notationsRepository.save(notation)
        model.name = "Versions Round Model Archived"
        modelsRepository.save(model)

        val ownerB = persistUser(email = "package-import-versions-b@test.com")
        authAs(ownerB.id!!, Role.USER)

        val response = importService.importPackage(zipBytes, ownerB)
        val newFileId = response.fileIdMap.getValue(fileId)
        val newLinkedId = response.fileIdMap.getValue(linkedId)

        val importedVersions = FileStorageTestConfiguration.versionBlobs[newFileId]
        assertNotNull(importedVersions)
        assertEquals(3, importedVersions.size)
        assertEquals(v1, importedVersions[0].toString(Charsets.UTF_8))
        assertTrue(importedVersions[1].toString(Charsets.UTF_8).contains("mdfile://$newLinkedId"))
        assertTrue(!importedVersions[1].toString(Charsets.UTF_8).contains("mdfile://$linkedId"))
        assertTrue(importedVersions[2].toString(Charsets.UTF_8).contains("mdfile://$newLinkedId"))
        assertEquals(v3.replace(linkedId.toString(), newLinkedId.toString()), importedVersions[2].toString(Charsets.UTF_8))

        val listed = fileStorageService.listVersions(newFileId)
        assertEquals(3, listed.size)
        assertEquals(listOf(3, 2, 1), listed.map { it.versionNumber })
    }

    @Test
    fun `import reuses existing compatible notation`() {
        val owner = persistUser(email = "package-import-notation-reuse@test.com")
        authAs(owner.id!!, Role.USER)

        val notation = persistNotation(owner = owner, name = "Reuse Notation", version = "3.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Reuse Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = owner, name = "Reuse Component")

        val model = persistModel(owner = owner, name = "Reuse Notation Model", version = "1.0.0")
        val node = persistNode(model = model, owner = owner, nodeType = nodeType, name = "Root")
        persistDiagram(model = model, notation = notation, owner = owner, node = node, name = "D")

        val zipBytes = exportService.export(model.id!!)
        model.name = "Reuse Notation Model Renamed"
        modelsRepository.save(model)

        val beforeNotations = notationsRepository.count()
        val response = importService.importPackage(zipBytes, owner)
        assertEquals(notation.id, response.notationIdMap[notation.id])
        assertEquals(beforeNotations, notationsRepository.count())
        assertTrue(response.warnings.any { it.contains("Reused notation 'Reuse Notation' v3.0.0") })
        assertEquals("Reuse Notation Model", response.modelName)
    }

    @Test
    fun `import forbids reuse when existing notation is not viewable`() {
        val ownerA = persistUser(email = "package-import-notation-forbidden-a@test.com")
        authAs(ownerA.id!!, Role.USER)

        val notation = persistNotation(owner = ownerA, name = "Forbidden Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = ownerA, name = "Forbidden Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = ownerA, name = "Forbidden Component")

        val model = persistModel(owner = ownerA, name = "Forbidden Notation Model", version = "1.0.0")
        val node = persistNode(model = model, owner = ownerA, nodeType = nodeType, name = "Root")
        persistDiagram(model = model, notation = notation, owner = ownerA, node = node, name = "D")

        val zipBytes = exportService.export(model.id!!)
        model.name = "Forbidden Notation Model Archived"
        modelsRepository.save(model)

        val ownerB = persistUser(email = "package-import-notation-forbidden-b@test.com")
        authAs(ownerB.id!!, Role.USER)

        val ex = assertThrows<PackageImportConflictException> {
            importService.importPackage(zipBytes, ownerB)
        }
        assertEquals("NOTATION_EXISTS_FORBIDDEN", ex.code)
        assertEquals("notation", ex.conflict.entity)
        assertEquals("Forbidden Notation", ex.conflict.name)
    }

    @Test
    fun `import fails when existing notation is incompatible`() {
        val owner = persistUser(email = "package-import-notation-incompatible@test.com")
        authAs(owner.id!!, Role.USER)

        val notation = persistNotation(owner = owner, name = "Incompat Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Incompat Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = owner, name = "Packaged Component")

        val model = persistModel(owner = owner, name = "Incompat Notation Model", version = "1.0.0")
        val node = persistNode(model = model, owner = owner, nodeType = nodeType, name = "Root")
        persistDiagram(model = model, notation = notation, owner = owner, node = node, name = "D")

        val zipBytes = exportService.export(model.id!!)
        model.name = "Incompat Notation Model Archived"
        modelsRepository.save(model)

        // Break compatibility: same notation name+version, different component name.
        val components = componentsRepository.findByNotation(notation, org.springframework.data.domain.Pageable.unpaged()).content
        val component = components.first()
        component.name = "Different Component"
        componentsRepository.save(component)

        val ex = assertThrows<PackageImportConflictException> {
            importService.importPackage(zipBytes, owner)
        }
        assertEquals("NOTATION_INCOMPATIBLE", ex.code)
        assertTrue(ex.conflict.details.any { it.contains("Packaged Component") })
    }

    @Test
    fun `import applies model name override when model name version exists`() {
        val owner = persistUser(email = "package-import-model-override@test.com")
        authAs(owner.id!!, Role.USER)

        val notation = persistNotation(owner = owner, name = "Override Model Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Override Model Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = owner)

        val model = persistModel(owner = owner, name = "Override Package Model", version = "2.0.0")
        val node = persistNode(model = model, owner = owner, nodeType = nodeType, name = "Root")
        persistDiagram(model = model, notation = notation, owner = owner, node = node, name = "D")

        val zipBytes = exportService.export(model.id!!)
        notation.name = "Override Model Notation Archived"
        notationsRepository.save(notation)

        val ex = assertThrows<PackageImportConflictException> {
            importService.importPackage(zipBytes, owner)
        }
        assertEquals("MODEL_EXISTS", ex.code)
        assertEquals("2.1.0", ex.conflict.suggestedVersion)

        val response = importService.importPackage(
            zipBytes,
            owner,
            overrides = ru.kavader.arepos.dto.modelpackage.ModelPackageImportOverrides(
                targetModelName = "Override Package Model Copy"
            )
        )
        assertEquals("Override Package Model Copy", response.modelName)
        assertEquals("2.0.0", response.modelVersion)
    }

    @Test
    fun `import conflicts on existing model name and version`() {
        val owner = persistUser(email = "package-import-model-conflict@test.com")
        authAs(owner.id!!, Role.USER)

        val notation = persistNotation(owner = owner, name = "Model Conflict Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Model Conflict Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = owner)

        val model = persistModel(owner = owner, name = "Model Conflict Package", version = "2.0.0")
        val node = persistNode(model = model, owner = owner, nodeType = nodeType, name = "Root")
        persistDiagram(model = model, notation = notation, owner = owner, node = node, name = "D")

        val zipBytes = exportService.export(model.id!!)

        // Free notation name+version, keep model conflict.
        notation.name = "Model Conflict Notation Archived"
        notationsRepository.save(notation)

        val beforeModels = modelsRepository.count()
        val ex = assertThrows<PackageImportConflictException> {
            importService.importPackage(zipBytes, owner)
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertEquals("MODEL_EXISTS", ex.code)
        assertTrue(ex.reason!!.contains("Model"))
        assertEquals(beforeModels, modelsRepository.count())
        assertTrue(notationsRepository.findAll().none { it.name == "Model Conflict Notation" && it.version == "1.0.0" })
    }

    @Test
    fun `import rejects unsupported manifest format`() {
        val owner = persistUser(email = "package-import-bad-format@test.com")
        authAs(owner.id!!, Role.USER)

        val zipBytes = buildZip(
            mapOf(
                "manifest.json" to manifestBytes(format = "legacy-model-package"),
                "model.json" to minimalModelBytes()
            )
        )

        val ex = assertThrows<ResponseStatusException> {
            importService.importPackage(zipBytes, owner)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertTrue(ex.reason!!.contains("Unsupported package format"))
    }

    @Test
    fun `import maps folder Directory type omitted from notation package`() {
        val ownerA = persistUser(email = "package-import-folder-a@test.com")
        authAs(ownerA.id!!, Role.USER)

        val directoryType = persistNodeType(
            owner = ownerA,
            name = "Directory",
            attrs = """{"kind":"directory","system":true}"""
        )
        val businessType = persistNodeType(owner = ownerA, name = "Folder Import Actor")
        val notation = persistNotation(owner = ownerA, name = "Folder Import Notation", version = "1.0.0")
        persistComponent(notation = notation, nodeType = businessType, owner = ownerA, name = "Actor")

        val model = persistModel(owner = ownerA, name = "Folder Import Model", version = "1.0.0")
        val root = persistNode(
            model = model,
            owner = ownerA,
            nodeType = directoryType,
            name = "Root",
            attrs = """{"system":{"hiddenTreeRoot":true},"treeOrder":0}"""
        )
        val folder = persistNode(
            model = model,
            owner = ownerA,
            nodeType = directoryType,
            name = "Home Path",
            parent = root,
            attrs = """{"treeOrder":0}"""
        )
        persistNode(
            model = model,
            owner = ownerA,
            nodeType = businessType,
            name = "Home",
            parent = folder,
            attrs = """{"treeOrder":0}"""
        )
        persistDiagram(
            model = model,
            notation = notation,
            owner = ownerA,
            node = folder,
            name = "Main",
            version = "1.0.0"
        )

        val zipBytes = exportService.export(model.id!!)

        notation.name = "Folder Import Notation Archived"
        notationsRepository.save(notation)
        model.name = "Folder Import Model Archived"
        modelsRepository.save(model)

        val ownerB = persistUser(email = "package-import-folder-b@test.com")
        authAs(ownerB.id!!, Role.USER)

        // Shared Testcontainers DB across Spring contexts can miss the liquibase seed; ensure it exists.
        val systemOwner = usersRepository.findByEmailIgnoreCase(SystemRootNodeTypeService.SYSTEM_OWNER_EMAIL)
            ?: persistUser(email = SystemRootNodeTypeService.SYSTEM_OWNER_EMAIL)
        val systemDirectory = systemRootNodeTypeService.getOrCreate(systemOwner, Instant.now())
        assertNotNull(systemDirectory.id)
        assertEquals("Directory", systemDirectory.name)

        val response = importService.importPackage(zipBytes, ownerB)

        val importedNodes = nodesRepository.findByModelIdOrdered(response.modelId, Pageable.unpaged()).content
        assertEquals(3, importedNodes.size)
        assertTrue(importedNodes.any { it.name == "Home Path" && it.parentNode != null })
        assertTrue(importedNodes.any { it.name == "Home" && it.parentNode != null })
        val folderNode = importedNodes.first { it.name == "Home Path" }
        assertEquals("Directory", folderNode.nodeType.name)
        assertEquals(systemDirectory.id, folderNode.nodeType.id)
        // Must not create a second Directory owned by the importer.
        kotlin.test.assertNull(nodeTypesRepository.findByOwnerAndNameIgnoreCase(ownerB, "Directory"))
    }

    @Test
    fun `import rejects unsupported manifest version`() {
        val owner = persistUser(email = "package-import-bad-version@test.com")
        authAs(owner.id!!, Role.USER)

        val zipBytes = buildZip(
            mapOf(
                "manifest.json" to manifestBytes(version = 99),
                "model.json" to minimalModelBytes()
            )
        )

        val ex = assertThrows<ResponseStatusException> {
            importService.importPackage(zipBytes, owner)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertTrue(ex.reason!!.contains("Unsupported package version"))
    }

    @Test
    fun `import rejects empty zip`() {
        val owner = persistUser(email = "package-import-empty-zip@test.com")
        authAs(owner.id!!, Role.USER)

        val ex = assertThrows<ResponseStatusException> {
            importService.importPackage(ByteArray(0), owner)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertTrue(ex.reason!!.contains("empty"))
    }

    @Test
    fun `import rejects invalid zip bytes`() {
        val owner = persistUser(email = "package-import-invalid-zip@test.com")
        authAs(owner.id!!, Role.USER)

        val ex = assertThrows<ResponseStatusException> {
            importService.importPackage(
                byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00),
                owner
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertTrue(
            ex.reason!!.contains("Invalid package ZIP") ||
                ex.reason!!.contains("Package ZIP has no entries")
        )
    }

    @Test
    fun `import rejects package exceeding diagram count limit`() {
        val owner = persistUser(email = "package-import-diagram-limit@test.com")
        authAs(owner.id!!, Role.USER)

        val notationId = UUID.randomUUID()
        val zipBytes = buildZip(
            mapOf(
                "manifest.json" to manifestBytes(notationIds = listOf(notationId)),
                "model.json" to minimalModelBytes(
                    diagramCount = ModelPackageLimits.MAX_DIAGRAMS + 1,
                    notationId = notationId
                )
            )
        )

        val ex = assertThrows<ResponseStatusException> {
            importService.importPackage(zipBytes, owner)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertTrue(ex.reason!!.contains("diagram limit"))
    }

    @Test
    fun `import rejects package exceeding notation count limit`() {
        val owner = persistUser(email = "package-import-notation-limit@test.com")
        authAs(owner.id!!, Role.USER)

        val entries = linkedMapOf(
            "manifest.json" to manifestBytes(),
            "model.json" to minimalModelBytes()
        )
        repeat(ModelPackageLimits.MAX_NOTATIONS + 1) {
            entries["notations/${UUID.randomUUID()}.json"] = "{}".toByteArray()
        }

        val ex = assertThrows<ResponseStatusException> {
            importService.importPackage(buildZip(entries), owner)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertTrue(ex.reason!!.contains("notation limit"))
    }

    private fun manifestBytes(
        format: String = ModelPackageLimits.FORMAT,
        version: Int = ModelPackageLimits.VERSION,
        notationIds: List<UUID> = emptyList()
    ): ByteArray {
        val manifest = ModelPackageManifest(
            format = format,
            version = version,
            exportedAt = Instant.parse("2026-01-01T00:00:00Z"),
            source = ModelPackageSource(
                modelId = UUID.randomUUID(),
                modelName = "Package",
                modelVersion = "1.0.0"
            ),
            notationIds = notationIds,
            fileIds = emptyList()
        )
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest)
    }

    private fun minimalModelBytes(
        diagramCount: Int = 0,
        notationId: UUID = UUID.randomUUID()
    ): ByteArray {
        val rootId = UUID.randomUUID()
        val model = PackagedModel(
            name = "Limit Test Model",
            version = "1.0.0",
            nodes = listOf(
                PackagedNode(
                    id = rootId,
                    stableId = UUID.randomUUID(),
                    name = "Root",
                    nodeTypeId = UUID.randomUUID(),
                    parentNodeId = null
                )
            ),
            diagrams = (1..diagramCount).map { index ->
                PackagedDiagram(
                    id = UUID.randomUUID(),
                    name = "Diagram $index",
                    version = "1.0.0",
                    notationId = notationId
                )
            }
        )
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(model)
    }

    private fun buildZip(entries: Map<String, ByteArray>): ByteArray {
        ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                for ((name, bytes) in entries) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            return baos.toByteArray()
        }
    }

    private fun persistWikiFile(owner: Users, id: UUID, filename: String, content: String): Files =
        filesRepository.save(
            Files(
                id = id,
                owner = owner,
                filename = filename,
                contentType = "text/markdown",
                size = content.toByteArray().size.toLong(),
                objectKey = "markdown/${owner.id}/$id/$filename",
                createdAt = Instant.now()
            )
        )

    private fun stubFileBlob(file: Files, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        FileStorageTestConfiguration.blobs[file.id] = bytes
        FileStorageTestConfiguration.versionBlobs[file.id] = mutableListOf(bytes.copyOf())
        `when`(fileStorageService.getFile(file.id)).thenReturn(
            file to ByteArrayResource(bytes)
        )
        `when`(fileStorageService.getFileMetadata(file.id)).thenReturn(file)
        `when`(fileStorageService.listVersions(file.id)).thenReturn(emptyList())
    }

    private fun stubFileVersions(file: Files, versionsOldestFirst: List<String>) {
        require(versionsOldestFirst.isNotEmpty())
        val versionBytes = versionsOldestFirst.map { it.toByteArray(Charsets.UTF_8) }.toMutableList()
        FileStorageTestConfiguration.blobs[file.id] = versionBytes.last().copyOf()
        FileStorageTestConfiguration.versionBlobs[file.id] = versionBytes.map { it.copyOf() }.toMutableList()
        val infos = versionsOldestFirst.mapIndexed { index, content ->
            FileStorageService.FileVersionInfo(
                versionNumber = index + 1,
                createdAt = Instant.now(),
                createdBy = file.owner.id!!,
                size = content.toByteArray(Charsets.UTF_8).size.toLong()
            )
        }
        `when`(fileStorageService.listVersions(file.id)).thenReturn(infos.asReversed())
        `when`(fileStorageService.getFile(file.id)).thenReturn(
            file to ByteArrayResource(versionBytes.last())
        )
        `when`(fileStorageService.getFileMetadata(file.id)).thenReturn(file)
        versionsOldestFirst.forEachIndexed { index, content ->
            `when`(fileStorageService.getFileVersion(file.id, index + 1)).thenReturn(
                file to ByteArrayResource(content.toByteArray(Charsets.UTF_8))
            )
        }
    }

    private fun authAs(userId: UUID, role: Role) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            userId,
            "n/a",
            listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
        )
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FileStorageTestConfiguration {
        companion object {
            val blobs = ConcurrentHashMap<UUID, ByteArray>()
            val versionBlobs = ConcurrentHashMap<UUID, MutableList<ByteArray>>()
        }

        @Bean
        fun fileStorageService(filesRepository: FilesRepository): FileStorageService {
            val mock = org.mockito.Mockito.mock(FileStorageService::class.java)
            val dummyOwner = Users(email = "file-storage-mock@test.com", role = Role.USER)

            doAnswer { invocation ->
                val id = invocation.getArgument<UUID>(0)
                val content = invocation.getArgument<ByteArray>(1)
                val filename = invocation.getArgument<String>(2)
                val contentType = invocation.getArgument<String>(3)
                val owner = invocation.getArgument<Users>(4)
                val saved = filesRepository.save(
                    Files(
                        id = id,
                        owner = owner,
                        filename = filename,
                        contentType = contentType,
                        size = content.size.toLong(),
                        objectKey = "test/${owner.id}/$id/$filename",
                        createdAt = Instant.now()
                    )
                )
                blobs[id] = content.copyOf()
                versionBlobs[id] = mutableListOf(content.copyOf())
                saved
            }.`when`(mock).createOwnedBlob(
                any(UUID::class.java) ?: UUID.randomUUID(),
                any(ByteArray::class.java) ?: ByteArray(0),
                any(String::class.java) ?: "file",
                any(String::class.java) ?: "application/octet-stream",
                any(Users::class.java) ?: dummyOwner
            )

            doAnswer { invocation ->
                val id = invocation.getArgument<UUID>(0)
                val content = invocation.getArgument<ByteArray>(1)
                val file = filesRepository.findById(id).orElse(null)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $id")
                file.size = content.size.toLong()
                file.createdAt = Instant.now()
                val saved = filesRepository.save(file)
                blobs[id] = content.copyOf()
                versionBlobs.compute(id) { _, existing ->
                    val list = existing ?: mutableListOf()
                    list.add(content.copyOf())
                    list
                }
                saved
            }.`when`(mock).appendOwnedBlobVersion(
                any(UUID::class.java) ?: UUID.randomUUID(),
                any(ByteArray::class.java) ?: ByteArray(0),
                any(Users::class.java) ?: dummyOwner
            )

            doAnswer { invocation ->
                val id = invocation.getArgument<UUID>(0)
                val file = filesRepository.findById(id).orElse(null) ?: return@doAnswer null
                val content = blobs[id] ?: ByteArray(0)
                file to ByteArrayResource(content)
            }.`when`(mock).getFile(any(UUID::class.java) ?: UUID.randomUUID())

            doAnswer { invocation ->
                val id = invocation.getArgument<UUID>(0)
                val versionNumber = invocation.getArgument<Int>(1)
                val file = filesRepository.findById(id).orElse(null) ?: return@doAnswer null
                val versions = versionBlobs[id] ?: return@doAnswer null
                val content = versions.getOrNull(versionNumber - 1) ?: return@doAnswer null
                file to ByteArrayResource(content)
            }.`when`(mock).getFileVersion(
                any(UUID::class.java) ?: UUID.randomUUID(),
                org.mockito.ArgumentMatchers.anyInt()
            )

            doAnswer { invocation ->
                val id = invocation.getArgument<UUID>(0)
                val file = filesRepository.findById(id).orElse(null) ?: return@doAnswer emptyList<FileStorageService.FileVersionInfo>()
                val versions = versionBlobs[id] ?: return@doAnswer emptyList<FileStorageService.FileVersionInfo>()
                versions.mapIndexed { index, bytes ->
                    FileStorageService.FileVersionInfo(
                        versionNumber = index + 1,
                        createdAt = Instant.now(),
                        createdBy = file.owner.id!!,
                        size = bytes.size.toLong()
                    )
                }.asReversed()
            }.`when`(mock).listVersions(any(UUID::class.java) ?: UUID.randomUUID())

            doAnswer { invocation ->
                val id = invocation.getArgument<UUID>(0)
                filesRepository.findById(id).orElse(null)
            }.`when`(mock).getFileMetadata(any(UUID::class.java) ?: UUID.randomUUID())

            doNothing().`when`(mock).deleteObjectQuietly(any(String::class.java) ?: "object")

            return mock
        }
    }
}
