package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.RepositoryTestBase
import ru.kavader.arepos.service.FileStorageService
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(ModelPackageExportServiceTest.FileStorageTestConfiguration::class)
class ModelPackageExportServiceTest : RepositoryTestBase() {

    @Autowired
    lateinit var exportService: ModelPackageExportService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var filesRepository: FilesRepository

    @Autowired
    lateinit var documentRefsRepository: DocumentRefsRepository

    @Autowired
    lateinit var fileStorageService: FileStorageService

    @AfterEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `export zip contains manifest model notations and files`() {
        val owner = persistUser(email = "package-export-owner@test.com")
        authAs(owner.id!!, Role.USER)

        val notation = persistNotation(owner = owner, name = "Pkg Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Pkg Node Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = owner, name = "Pkg Component")

        val secondFileId = UUID.randomUUID()
        val firstFileId = UUID.randomUUID()
        val secondContent = "Second wiki page"
        val firstContent = "See [second](mdfile://$secondFileId)"

        val secondFile = filesRepository.save(
            Files(
                id = secondFileId,
                owner = owner,
                filename = "second.md",
                contentType = "text/markdown",
                size = secondContent.toByteArray().size.toLong(),
                objectKey = "markdown/${owner.id}/$secondFileId/second.md",
                createdAt = Instant.now()
            )
        )
        val firstFile = filesRepository.save(
            Files(
                id = firstFileId,
                owner = owner,
                filename = "first.md",
                contentType = "text/markdown",
                size = firstContent.toByteArray().size.toLong(),
                objectKey = "markdown/${owner.id}/$firstFileId/first.md",
                createdAt = Instant.now()
            )
        )
        stubFileBlob(firstFile, firstContent)
        stubFileBlob(secondFile, secondContent)

        val model = persistModel(
            owner = owner,
            name = "Package Model",
            version = "2.0.0",
            attrs = """{"documentFileId":"$firstFileId"}"""
        )
        val node = persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "Business Actor",
            attrs = """{"label":"actor"}"""
        )
        persistDiagram(
            model = model,
            notation = notation,
            owner = owner,
            node = node,
            name = "Main",
            version = "1.0.0"
        )
        documentRefsRepository.save(
            DocumentRefs(
                file = firstFile,
                createdBy = owner,
                createdAt = Instant.now(),
                model = model
            )
        )

        val zipBytes = exportService.export(model.id!!)
        val entries = readZipEntries(zipBytes)

        assertTrue(entries.containsKey("manifest.json"))
        assertTrue(entries.containsKey("model.json"))
        assertTrue(entries.containsKey("document-refs.json"))
        assertTrue(entries.containsKey("notations/${notation.id}.json"))
        assertTrue(entries.containsKey("files/$firstFileId/meta.json"))
        assertTrue(entries.containsKey("files/$firstFileId/blob"))
        assertTrue(entries.containsKey("files/$secondFileId/meta.json"))
        assertTrue(entries.containsKey("files/$secondFileId/blob"))

        val manifest = objectMapper.readTree(entries["manifest.json"])
        assertEquals(ModelPackageLimits.FORMAT, manifest.path("format").asText())
        assertEquals(ModelPackageLimits.VERSION, manifest.path("version").asInt())
        assertEquals(model.id.toString(), manifest.path("source").path("modelId").asText())
        assertEquals("Package Model", manifest.path("source").path("modelName").asText())
        assertEquals("2.0.0", manifest.path("source").path("modelVersion").asText())

        val modelJson = objectMapper.readTree(entries["model.json"])
        assertEquals("Package Model", modelJson.path("name").asText())
        assertEquals(1, modelJson.path("nodes").size())
        assertEquals(1, modelJson.path("diagrams").size())

        assertEquals(firstContent, entries["files/$firstFileId/blob"]!!.toString(Charsets.UTF_8))
        assertEquals(secondContent, entries["files/$secondFileId/blob"]!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `export returns 400 when node type is not covered by diagram notations`() {
        val owner = persistUser(email = "package-export-orphan@test.com")
        authAs(owner.id!!, Role.USER)

        val notation = persistNotation(owner = owner, name = "Covered Notation", version = "1.0.0")
        val coveredType = persistNodeType(owner = owner, name = "Covered Type")
        persistComponent(notation = notation, nodeType = coveredType, owner = owner)

        val orphanType = persistNodeType(owner = owner, name = "Orphan Type")
        val model = persistModel(owner = owner, name = "Orphan Model", version = "1.0.0")
        persistNode(model = model, owner = owner, nodeType = orphanType, name = "Orphan Node")
        persistDiagram(model = model, notation = notation, owner = owner, name = "Diagram")

        val ex = assertThrows<ResponseStatusException> {
            exportService.export(model.id!!)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertNotNull(ex.reason)
        assertTrue(ex.reason!!.contains(orphanType.id.toString()))
        assertTrue(ex.reason!!.contains("not included in diagram notations"))
    }

    @Test
    fun `export returns 403 when notation is not readable`() {
        val modelOwner = persistUser(email = "package-export-model-owner@test.com")
        val notationOwner = persistUser(email = "package-export-notation-owner@test.com")
        authAs(modelOwner.id!!, Role.USER)

        val notation = persistNotation(owner = notationOwner, name = "Foreign Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = notationOwner, name = "Foreign Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = notationOwner)

        val model = persistModel(owner = modelOwner, name = "Shared Model", version = "1.0.0")
        // Model is owned by modelOwner (viewable); notation is foreign and not shared
        // (diagram-mediated notation view must not grant package export).
        persistNode(model = model, owner = modelOwner, nodeType = nodeType, name = "Node")
        persistDiagram(model = model, notation = notation, owner = modelOwner, name = "Diagram")

        val ex = assertThrows<ResponseStatusException> {
            exportService.export(model.id!!)
        }
        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
    }

    @Test
    fun `export returns 500 when referenced file blob is missing`() {
        val owner = persistUser(email = "package-export-missing-blob@test.com")
        authAs(owner.id!!, Role.USER)

        val fileId = UUID.randomUUID()
        val file = filesRepository.save(
            Files(
                id = fileId,
                owner = owner,
                filename = "missing.md",
                contentType = "text/markdown",
                size = 12,
                objectKey = "markdown/${owner.id}/$fileId/missing.md",
                createdAt = Instant.now()
            )
        )
        `when`(fileStorageService.getFileMetadata(file.id)).thenReturn(file)
        `when`(fileStorageService.getFile(file.id)).thenReturn(null)

        val notation = persistNotation(owner = owner, name = "Missing Blob Notation", version = "1.0.0")
        val nodeType = persistNodeType(owner = owner, name = "Missing Blob Type")
        persistComponent(notation = notation, nodeType = nodeType, owner = owner)

        val model = persistModel(
            owner = owner,
            name = "Missing Blob Model",
            version = "1.0.0",
            attrs = """{"documentFileId":"$fileId"}"""
        )
        persistNode(model = model, owner = owner, nodeType = nodeType, name = "Node")
        persistDiagram(model = model, notation = notation, owner = owner, name = "Diagram")

        val ex = assertThrows<ResponseStatusException> {
            exportService.export(model.id!!)
        }
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.statusCode)
        assertNotNull(ex.reason)
        assertTrue(ex.reason!!.contains("Referenced file blob missing"))
        assertTrue(ex.reason!!.contains(fileId.toString()))
    }

    private fun authAs(userId: UUID, role: Role) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            userId,
            "n/a",
            listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
        )
    }

    private fun stubFileBlob(file: Files, content: String) {
        `when`(fileStorageService.getFile(file.id)).thenReturn(
            file to ByteArrayResource(content.toByteArray(Charsets.UTF_8))
        )
        `when`(fileStorageService.getFileMetadata(file.id)).thenReturn(file)
    }

    private fun readZipEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                result[entry.name] = zis.readBytes()
                zis.closeEntry()
            }
        }
        return result
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FileStorageTestConfiguration {
        @Bean
        fun fileStorageService(): FileStorageService =
            org.mockito.Mockito.mock(FileStorageService::class.java)
    }
}
