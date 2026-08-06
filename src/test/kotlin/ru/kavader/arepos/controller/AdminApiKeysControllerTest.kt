package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.apikey.ApiKeyModes
import ru.kavader.arepos.dto.apikey.CreateApiKeyRequest
import ru.kavader.arepos.dto.apikey.CreateApiKeyResponse
import ru.kavader.arepos.dto.apikey.ExchangeApiKeyRequest
import ru.kavader.arepos.dto.auth.RegisterRequest
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiKeysControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @MockitoSpyBean
    lateinit var accessService: ResourceAccessService

    @BeforeEach
    fun setupCerbosMock() {
        doAnswer { CurrentUser.getRole() == "ADMIN" }
            .`when`(accessService)
            .canViewAdminPanel()
    }

    private fun registerAndGetUserId(email: String): UUID {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        RegisterRequest(
                            email = email,
                            password = "ValidPass1",
                            firstName = "Test",
                            lastName = "User"
                        )
                    )
                )
        ).andExpect(status().isCreated)

        return usersRepository.findByEmailIgnoreCase(email)!!.id!!
    }

    private fun createKeyForUser(userId: UUID): CreateApiKeyResponse {
        val result = mockMvc.perform(
            post("/api/v1/api-keys")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateApiKeyRequest(
                            name = "target-key",
                            mode = ApiKeyModes.ALL,
                            scopes = listOf("models:read")
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString)
    }

    @Test
    fun `admin can list and revoke another user api key`() {
        val targetUserId = registerAndGetUserId("admin-apikeys-target@test.com")
        val created = createKeyForUser(targetUserId)

        val admin = usersRepository.save(
            Users(
                email = "admin-apikeys-admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/admin/users/$targetUserId/api-keys")
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(created.apiKey.id.toString()))
            .andExpect(jsonPath("$.items[0].name").value("target-key"))

        mockMvc.perform(
            delete("/api/v1/admin/users/$targetUserId/api-keys/${created.apiKey.id}")
                .withAuth(admin.id!!, Role.ADMIN)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `non-admin cannot list or revoke another user api keys`() {
        val targetUserId = registerAndGetUserId("admin-apikeys-forbidden-target@test.com")
        val created = createKeyForUser(targetUserId)

        val regularUser = usersRepository.save(
            Users(
                email = "admin-apikeys-forbidden-user@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/admin/users/$targetUserId/api-keys")
                .withAuth(regularUser.id!!, Role.USER)
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            delete("/api/v1/admin/users/$targetUserId/api-keys/${created.apiKey.id}")
                .withAuth(regularUser.id!!, Role.USER)
        ).andExpect(status().isForbidden)
    }
}
