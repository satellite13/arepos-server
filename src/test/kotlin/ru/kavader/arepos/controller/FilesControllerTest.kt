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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.multipart.MultipartFile
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
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

    private fun persistUser(email: String): Users =
        usersRepository.save(
            Users(
                email = email,
                role = Role.USER,
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
