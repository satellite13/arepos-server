package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.apikey.CreateApiKeyRequest
import ru.kavader.arepos.dto.apikey.CreateApiKeyResponse
import ru.kavader.arepos.dto.apikey.ExchangeApiKeyRequest
import ru.kavader.arepos.dto.apikey.ExchangeApiKeyResponse
import ru.kavader.arepos.dto.apikey.UpdateApiKeyRequest
import ru.kavader.arepos.dto.auth.RegisterRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.TokenType
import java.time.Instant
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeysControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

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

    private fun createKey(
        userId: UUID,
        scopes: List<String> = listOf("models:read"),
        modelIds: List<UUID>? = null
    ): CreateApiKeyResponse {
        val result = mockMvc.perform(
            post("/api/v1/api-keys")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateApiKeyRequest(
                            name = "mcp-key",
                            scopes = scopes,
                            modelIds = modelIds
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.key").isNotEmpty)
            .andExpect(jsonPath("$.apiKey.tokenPrefix").isNotEmpty)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsString)
    }

    @Test
    fun `creates lists and revokes api key`() {
        val userId = registerAndGetUserId("apikey-owner@test.com")
        val created = createKey(userId, listOf("models:read", "models:write"))

        mockMvc.perform(get("/api/v1/api-keys").withAuth(userId, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("mcp-key"))
            .andExpect(jsonPath("$.items[0].scopes.length()").value(2))

        mockMvc.perform(
            patch("/api/v1/api-keys/${created.apiKey.id}")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateApiKeyRequest(name = "renamed")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("renamed"))

        mockMvc.perform(delete("/api/v1/api-keys/${created.apiKey.id}").withAuth(userId, Role.USER))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `exchanges api key for mcp access jwt`() {
        val userId = registerAndGetUserId("apikey-exchange@test.com")
        val created = createKey(userId, listOf("models:read"))

        val exchangeResult = mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.expiresIn").isNumber)
            .andReturn()

        val exchange = objectMapper.readValue<ExchangeApiKeyResponse>(exchangeResult.response.contentAsString)
        assertTrue(jwtTokenProvider.validateToken(exchange.accessToken))
        assertTrue(jwtTokenProvider.getTokenType(exchange.accessToken) == TokenType.MCP_ACCESS)
        assertTrue(jwtTokenProvider.getScopes(exchange.accessToken).contains("models:read"))
    }

    @Test
    fun `mcp token without write scope cannot mutate`() {
        val userId = registerAndGetUserId("apikey-readonly@test.com")
        val created = createKey(userId, listOf("models:read"))
        val exchangeResult = mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        ).andExpect(status().isOk).andReturn()
        val exchange = objectMapper.readValue<ExchangeApiKeyResponse>(exchangeResult.response.contentAsString)

        mockMvc.perform(
            post("/api/v1/models")
                .header("Authorization", "Bearer ${exchange.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"blocked","version":"1.0.0"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `mcp token allowlist blocks other models`() {
        val userId = registerAndGetUserId("apikey-allowlist@test.com")
        val user = usersRepository.findById(userId).orElseThrow()
        val allowed = modelsRepository.save(
            Models(
                name = "allowed-model",
                version = "1.0.0",
                createdAt = Instant.now(),
                owner = user
            )
        )
        val denied = modelsRepository.save(
            Models(
                name = "denied-model",
                version = "1.0.0",
                createdAt = Instant.now(),
                owner = user
            )
        )
        val created = createKey(userId, listOf("models:read"), modelIds = listOf(allowed.id!!))
        val exchangeResult = mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        ).andExpect(status().isOk).andReturn()
        val exchange = objectMapper.readValue<ExchangeApiKeyResponse>(exchangeResult.response.contentAsString)

        mockMvc.perform(
            get("/api/v1/models/${allowed.id}")
                .header("Authorization", "Bearer ${exchange.accessToken}")
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/models/${denied.id}")
                .header("Authorization", "Bearer ${exchange.accessToken}")
        ).andExpect(status().isForbidden)
    }
}
