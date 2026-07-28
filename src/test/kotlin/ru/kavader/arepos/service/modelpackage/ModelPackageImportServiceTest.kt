package ru.kavader.arepos.service.modelpackage

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
import ru.kavader.arepos.service.FileStorageService
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

    @AfterEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
        FileStorageTestConfiguration.blobs.clear()
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
    fun `import conflicts on existing notation name and version`() {
        val owner = persistUser(email = "package-import-notation-conflict@test.com")
        authAs(owner.id!!, Role.USER)

        val notation = persistNotation(owner = owner, name = "Conflict Notation", version = "3.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Conflict Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = owner)

        val model = persistModel(owner = owner, name = "Conflict Notation Model", version = "1.0.0")
        val node = persistNode(model = model, owner = owner, nodeType = nodeType, name = "Root")
        persistDiagram(model = model, notation = notation, owner = owner, node = node, name = "D")

        val zipBytes = exportService.export(model.id!!)
        model.name = "Conflict Notation Model Renamed"
        modelsRepository.save(model)

        val beforeModels = modelsRepository.count()
        val ex = assertThrows<ResponseStatusException> {
            importService.importPackage(zipBytes, owner)
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertTrue(ex.reason!!.contains("Notation"))
        assertEquals(beforeModels, modelsRepository.count())
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
        val ex = assertThrows<ResponseStatusException> {
            importService.importPackage(zipBytes, owner)
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertTrue(ex.reason!!.contains("Model"))
        assertEquals(beforeModels, modelsRepository.count())
        assertTrue(notationsRepository.findAll().none { it.name == "Model Conflict Notation" && it.version == "1.0.0" })
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
        FileStorageTestConfiguration.blobs[file.id] = content.toByteArray(Charsets.UTF_8)
        `when`(fileStorageService.getFile(file.id)).thenReturn(
            file to ByteArrayResource(content.toByteArray(Charsets.UTF_8))
        )
        `when`(fileStorageService.getFileMetadata(file.id)).thenReturn(file)
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
                val file = filesRepository.findById(id).orElse(null) ?: return@doAnswer null
                val content = blobs[id] ?: ByteArray(0)
                file to ByteArrayResource(content)
            }.`when`(mock).getFile(any(UUID::class.java) ?: UUID.randomUUID())

            doAnswer { invocation ->
                val id = invocation.getArgument<UUID>(0)
                filesRepository.findById(id).orElse(null)
            }.`when`(mock).getFileMetadata(any(UUID::class.java) ?: UUID.randomUUID())

            doNothing().`when`(mock).deleteObjectQuietly(any(String::class.java) ?: "object")

            return mock
        }
    }
}
