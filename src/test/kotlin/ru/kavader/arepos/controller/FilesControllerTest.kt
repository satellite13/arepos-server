package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.multipart.MultipartFile
import ru.kavader.arepos.model.DocumentRefs
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DocumentRefsRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.DocumentRefsService
import ru.kavader.arepos.service.FileStorageService
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(FilesControllerTest.FilesTestConfiguration::class)
class FilesControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var filesRepository: FilesRepository

    @Autowired
    lateinit var documentRefsRepository: DocumentRefsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Autowired
    lateinit var fileStorageService: FileStorageService

    @Test
    fun `authenticated user uploads and downloads file`() {
        val owner = persistUser("file-owner@test.com")
        val bytes = "# Audit report".toByteArray()
        val saved = file(owner, bytes.size.toLong())
        val upload = MockMultipartFile(
            "file",
            "audit.md",
            "text/markdown",
            bytes
        )
        val anyFile: MultipartFile? = any(MultipartFile::class.java)
        val anyOwner: Users? = any(Users::class.java)
        `when`(
            fileStorageService.upload(
                anyFile ?: upload,
                anyOwner ?: owner
            )
        ).thenReturn(saved)
        `when`(fileStorageService.getFileMetadata(saved.id)).thenReturn(saved)
        `when`(fileStorageService.getFile(saved.id))
            .thenReturn(saved to ByteArrayResource(bytes))

        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .file(upload)
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(saved.id.toString()))
            .andExpect(jsonPath("$.filename").value("audit.md"))
            .andExpect(jsonPath("$.contentType").value("text/markdown"))

        mockMvc.perform(
            get("/api/v1/files/${saved.id}")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_MARKDOWN))
            .andExpect(content().bytes(bytes))
    }

    @Test
    fun `other user cannot download owners file`() {
        val owner = persistUser("file-acl-owner@test.com")
        val other = persistUser("file-acl-other@test.com")
        val saved = file(owner, 4)
        `when`(fileStorageService.getFileMetadata(saved.id)).thenReturn(saved)

        mockMvc.perform(
            get("/api/v1/files/${saved.id}")
                .withAuth(other.id!!, Role.USER)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `model editor updates linked markdown without file share`() {
        val modelOwner = persistUser("linked-model-owner@test.com")
        val outsider = persistUser("linked-model-outsider@test.com")
        val model = persistModel(modelOwner, "Private architecture")
        val markdown = filesRepository.save(file(outsider, 42))
        documentRefsRepository.save(
            DocumentRefs(
                file = markdown,
                createdBy = modelOwner,
                createdAt = Instant.now(),
                model = model
            )
        )
        `when`(fileStorageService.getFileMetadata(markdown.id)).thenReturn(markdown)
        val anyFileId: UUID? = any(UUID::class.java)
        val anyContent: String? = any(String::class.java)
        val anyOwner: Users? = any(Users::class.java)
        `when`(
            fileStorageService.updateMarkdown(
                anyFileId ?: markdown.id!!,
                anyContent ?: "# Updated",
                anyOwner ?: modelOwner
            )
        )
            .thenReturn(markdown)

        mockMvc.perform(
            put("/api/v1/files/${markdown.id}/markdown")
                .withAuth(outsider.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"audit.md","content":"# Updated"}""")
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/api/v1/files/${markdown.id}/markdown")
                .withAuth(modelOwner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"audit.md","content":"# Updated"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(markdown.id.toString()))
    }

    @Test
    fun `model editor cannot update markdown linked to an inaccessible second model`() {
        val editor = persistUser("multi-ref-editor@test.com")
        val restrictedModelOwner = persistUser("multi-ref-restricted-owner@test.com")
        val editableModel = persistModel(editor, "Editable architecture")
        val restrictedModel = persistModel(restrictedModelOwner, "Restricted architecture")
        val markdown = filesRepository.save(file(editor, 42))
        documentRefsRepository.save(
            DocumentRefs(
                file = markdown,
                createdBy = editor,
                createdAt = Instant.now(),
                model = editableModel
            )
        )
        documentRefsRepository.save(
            DocumentRefs(
                file = markdown,
                createdBy = editor,
                createdAt = Instant.now(),
                model = restrictedModel
            )
        )
        `when`(fileStorageService.getFileMetadata(markdown.id)).thenReturn(markdown)

        mockMvc.perform(
            put("/api/v1/files/${markdown.id}/markdown")
                .withAuth(editor.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"audit.md","content":"# Updated"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `model editor updates markdown linked to two editable models without file share`() {
        val editor = persistUser("multi-ref-editor-allowed@test.com")
        val sharedModelOwner = persistUser("multi-ref-shared-owner@test.com")
        val fileOwner = persistUser("multi-ref-file-owner@test.com")
        val ownedModel = persistModel(editor, "Owned architecture")
        val sharedModel = persistModel(sharedModelOwner, "Shared architecture")
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = sharedModel.id!!,
                granteeUser = editor,
                grantedByUser = sharedModelOwner,
                permission = SharePermission.EDIT,
                createdAt = Instant.now()
            )
        )
        val markdown = filesRepository.save(file(fileOwner, 42))
        documentRefsRepository.save(
            DocumentRefs(
                file = markdown,
                createdBy = editor,
                createdAt = Instant.now(),
                model = ownedModel
            )
        )
        documentRefsRepository.save(
            DocumentRefs(
                file = markdown,
                createdBy = editor,
                createdAt = Instant.now(),
                model = sharedModel
            )
        )
        `when`(fileStorageService.getFileMetadata(markdown.id)).thenReturn(markdown)
        val anyFileId: UUID? = any(UUID::class.java)
        val anyContent: String? = any(String::class.java)
        val anyOwner: Users? = any(Users::class.java)
        `when`(
            fileStorageService.updateMarkdown(
                anyFileId ?: markdown.id!!,
                anyContent ?: "# Updated",
                anyOwner ?: editor
            )
        ).thenReturn(markdown)

        mockMvc.perform(
            put("/api/v1/files/${markdown.id}/markdown")
                .withAuth(editor.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"audit.md","content":"# Updated"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(markdown.id.toString()))
    }

    private fun persistUser(email: String): Users =
        usersRepository.save(
            Users(
                email = email,
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

    private fun persistModel(owner: Users, name: String): Models =
        modelsRepository.save(
            Models(
                name = name,
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

    private fun file(owner: Users, size: Long): Files =
        Files(
            id = UUID.randomUUID(),
            owner = owner,
            filename = "audit.md",
            contentType = "text/markdown",
            size = size,
            objectKey = "markdown/${owner.id}/audit.md",
            createdAt = Instant.now()
        )

    @TestConfiguration(proxyBeanMethods = false)
    class FilesTestConfiguration {
        @Bean
        fun fileStorageService(): FileStorageService =
            org.mockito.Mockito.mock(FileStorageService::class.java)

        @Bean
        fun filesController(
            fileStorageService: FileStorageService,
            usersRepository: UsersRepository,
            accessService: ResourceAccessService,
            documentRefsService: DocumentRefsService
        ): FilesController =
            FilesController(
                fileStorageService,
                usersRepository,
                accessService,
                documentRefsService
            )
    }
}
