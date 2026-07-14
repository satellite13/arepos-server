package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
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
import ru.kavader.arepos.repository.DownloadAssetRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.DownloadsService
import ru.kavader.arepos.service.FileStorageService
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(DownloadsControllerTest.DownloadsTestConfiguration::class)
class DownloadsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var fileStorageService: FileStorageService

    @Autowired
    lateinit var filesRepository: ru.kavader.arepos.repository.FilesRepository

    @Test
    fun `anonymous can list catalog but not download file`() {
        mockMvc.perform(get("/api/v1/downloads"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)

        mockMvc.perform(get("/api/v1/downloads/${UUID.randomUUID()}/file"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `admin uploads notation export and user downloads it`() {
        val admin = persistUser("downloads-admin@test.com", Role.ADMIN)
        val user = persistUser("downloads-user@test.com")
        val payload = """{"format":"warchi-notation-export","version":1}""".toByteArray()
        val stored = filesRepository.save(
            Files(
                id = UUID.randomUUID(),
                owner = admin,
                filename = "archimate.json",
                contentType = "application/json",
                size = payload.size.toLong(),
                objectKey = "site-downloads/${admin.id}/archimate.json",
                createdAt = Instant.now()
            )
        )
        val upload = MockMultipartFile(
            "file",
            "archimate.json",
            "application/json",
            payload
        )
        val anyFile: MultipartFile? = any(MultipartFile::class.java)
        val anyOwner: Users? = any(Users::class.java)
        `when`(fileStorageService.uploadSiteAsset(anyFile ?: upload, anyOwner ?: admin)).thenReturn(stored)
        `when`(fileStorageService.getFile(stored.id)).thenReturn(stored to ByteArrayResource(payload))

        val created = mockMvc.perform(
            multipart("/api/v1/downloads")
                .file(upload)
                .param("title", "ArchiMate starter")
                .param("description", "Sample notation")
                .param("kind", "notation_export")
                .param("versionLabel", "1.0.0")
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("ArchiMate starter"))
            .andExpect(jsonPath("$.kind").value("notation_export"))
            .andReturn()

        val id = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(get("/api/v1/downloads"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))

        mockMvc.perform(get("/api/v1/downloads/$id/file").withAuth(user.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().bytes(payload))
    }

    @Test
    fun `notation_export without format is rejected`() {
        val admin = persistUser("downloads-bad-json@test.com", Role.ADMIN)
        val upload = MockMultipartFile(
            "file",
            "bad.json",
            "application/json",
            """{"format":"other"}""".toByteArray()
        )
        mockMvc.perform(
            multipart("/api/v1/downloads")
                .file(upload)
                .param("title", "Bad")
                .param("kind", "notation_export")
                .withAuth(admin.id!!, Role.ADMIN)
        ).andExpect(status().isBadRequest)
    }

    private fun persistUser(email: String, role: Role = Role.USER): Users =
        usersRepository.save(Users(email = email, role = role, createdAt = Instant.now()))

    @TestConfiguration(proxyBeanMethods = false)
    class DownloadsTestConfiguration {
        @Bean
        fun fileStorageService(): FileStorageService =
            org.mockito.Mockito.mock(FileStorageService::class.java)

        @Bean
        fun downloadsService(
            downloadAssetRepository: DownloadAssetRepository,
            fileStorageService: FileStorageService,
            usersRepository: UsersRepository,
            accessService: ResourceAccessService,
            objectMapper: ObjectMapper
        ): DownloadsService =
            DownloadsService(
                downloadAssetRepository,
                fileStorageService,
                usersRepository,
                accessService,
                objectMapper
            )

        @Bean
        fun downloadsController(downloadsService: DownloadsService): DownloadsController =
            DownloadsController(downloadsService)
    }
}
