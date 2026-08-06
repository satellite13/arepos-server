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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.apikey.ApiKeyGrantDto
import ru.kavader.arepos.dto.apikey.ApiKeyModes
import ru.kavader.arepos.dto.apikey.CreateApiKeyRequest
import ru.kavader.arepos.dto.apikey.CreateApiKeyResponse
import ru.kavader.arepos.dto.apikey.ExchangeApiKeyRequest
import ru.kavader.arepos.dto.apikey.ExchangeApiKeyResponse
import ru.kavader.arepos.dto.apikey.UpdateApiKeyRequest
import ru.kavader.arepos.dto.auth.RegisterRequest
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.ApiKeysRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NotationsRepository
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
    lateinit var apiKeysRepository: ApiKeysRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

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
        grants: List<ApiKeyGrantDto>? = null
    ): CreateApiKeyResponse {
        val mode = if (grants != null) ApiKeyModes.GRANTS else ApiKeyModes.ALL
        val result = mockMvc.perform(
            post("/api/v1/api-keys")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateApiKeyRequest(
                            name = "mcp-key",
                            mode = mode,
                            scopes = if (mode == ApiKeyModes.ALL) scopes else null,
                            grants = grants
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

    private fun exchangeToken(key: String): String {
        val exchangeResult = mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(key)))
        ).andExpect(status().isOk).andReturn()
        val exchange = objectMapper.readValue<ExchangeApiKeyResponse>(exchangeResult.response.contentAsString)
        return exchange.accessToken
    }

    private fun persistModel(userId: UUID, name: String): Models {
        val user = usersRepository.findById(userId).orElseThrow()
        return modelsRepository.save(
            Models(
                name = name,
                version = "1.0.0",
                createdAt = Instant.now(),
                owner = user
            )
        )
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
        val created = createKey(
            userId,
            grants = listOf(ApiKeyGrantDto(modelId = allowed.id!!, scopes = listOf("models:read")))
        )
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

    @Test
    fun `mcp token allowlist blocks diagrams from other models`() {
        val userId = registerAndGetUserId("apikey-diagram-allowlist@test.com")
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
        val notation = notationsRepository.save(
            Notations(
                name = "notation-${UUID.randomUUID()}",
                version = "1.0.0",
                createdAt = Instant.now(),
                owner = user
            )
        )
        val deniedDiagram = diagramsRepository.save(
            Diagrams(
                name = "secret-diagram",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = user,
                model = denied,
                notation = notation
            )
        )
        diagramsRepository.save(
            Diagrams(
                name = "allowed-diagram",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = user,
                model = allowed,
                notation = notation
            )
        )

        val created = createKey(
            userId,
            grants = listOf(ApiKeyGrantDto(modelId = allowed.id!!, scopes = listOf("models:read")))
        )
        val exchangeResult = mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        ).andExpect(status().isOk).andReturn()
        val exchange = objectMapper.readValue<ExchangeApiKeyResponse>(exchangeResult.response.contentAsString)
        val mcpAuth = "Bearer ${exchange.accessToken}"

        mockMvc.perform(get("/api/v1/diagrams/${deniedDiagram.id}").header("Authorization", mcpAuth))
            .andExpect(status().isForbidden)

        mockMvc.perform(
            get("/api/v1/diagrams")
                .param("modelId", denied.id.toString())
                .header("Authorization", mcpAuth)
        ).andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/diagrams").header("Authorization", mcpAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.name=='secret-diagram')]").isEmpty)
            .andExpect(jsonPath("$.content[?(@.name=='allowed-diagram')]").isNotEmpty)
    }

    @Test
    fun `grants mode enforces per-model read and write scopes`() {
        val userId = registerAndGetUserId("apikey-grants-scopes@test.com")
        val modelA = persistModel(userId, "grants-scope-a")
        val modelB = persistModel(userId, "grants-scope-b")
        val modelOther = persistModel(userId, "grants-scope-other")

        val created = createKey(
            userId,
            grants = listOf(
                ApiKeyGrantDto(modelId = modelA.id!!, scopes = listOf("models:read")),
                ApiKeyGrantDto(modelId = modelB.id!!, scopes = listOf("models:read", "models:write"))
            )
        )
        val mcpAuth = "Bearer ${exchangeToken(created.key)}"

        mockMvc.perform(
            put("/api/v1/models/${modelA.id}")
                .header("Authorization", mcpAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"grants-scope-a-mutated"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("missing_scope"))

        mockMvc.perform(
            put("/api/v1/models/${modelB.id}")
                .header("Authorization", mcpAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"grants-scope-b-mutated"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/models/${modelOther.id}")
                .header("Authorization", mcpAuth)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("model_not_allowed"))
    }

    @Test
    fun `grants mode forbids creating models even with write scope`() {
        val userId = registerAndGetUserId("apikey-grants-create@test.com")
        val model = persistModel(userId, "grants-create-existing")
        val created = createKey(
            userId,
            grants = listOf(
                ApiKeyGrantDto(modelId = model.id!!, scopes = listOf("models:read", "models:write"))
            )
        )
        val mcpAuth = "Bearer ${exchangeToken(created.key)}"

        mockMvc.perform(
            post("/api/v1/models")
                .header("Authorization", mcpAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"grants-create-new","version":"1.0.0"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("missing_scope"))
    }

    @Test
    fun `grants mode forbids copying models even with write scope`() {
        val userId = registerAndGetUserId("apikey-grants-copy@test.com")
        val model = persistModel(userId, "grants-copy-existing")
        val created = createKey(
            userId,
            grants = listOf(
                ApiKeyGrantDto(modelId = model.id!!, scopes = listOf("models:read", "models:write"))
            )
        )
        val mcpAuth = "Bearer ${exchangeToken(created.key)}"

        mockMvc.perform(
            post("/api/v1/models/${model.id}/copy")
                .header("Authorization", mcpAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"grants-copy-new","version":"1.0.0"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("missing_scope"))
    }

    @Test
    fun `grants mode search catalog omits non-granted models`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val userId = registerAndGetUserId("apikey-grants-search-$suffix@test.com")
        val grantedName = "grants-search-granted-$suffix"
        val deniedName = "grants-search-denied-$suffix"
        val granted = persistModel(userId, grantedName)
        persistModel(userId, deniedName)

        val created = createKey(
            userId,
            grants = listOf(
                ApiKeyGrantDto(modelId = granted.id!!, scopes = listOf("models:read"))
            )
        )
        val mcpAuth = "Bearer ${exchangeToken(created.key)}"

        mockMvc.perform(
            get("/api/v1/search/catalog")
                .param("q", suffix)
                .param("kinds", "models")
                .header("Authorization", mcpAuth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits.length()").value(1))
            .andExpect(jsonPath("$.hits[0].id").value(granted.id.toString()))
            .andExpect(jsonPath("$.hits[0].name").value(grantedName))
            .andExpect(jsonPath("$.hits[?(@.name=='$deniedName')]").isEmpty)
    }

    @Test
    fun `grants mode normalizes write-only scopes to include read`() {
        val userId = registerAndGetUserId("apikey-grants-write-only@test.com")
        val model = persistModel(userId, "grants-write-only")

        val result = mockMvc.perform(
            post("/api/v1/api-keys")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateApiKeyRequest(
                            name = "write-only-grant",
                            mode = ApiKeyModes.GRANTS,
                            grants = listOf(
                                ApiKeyGrantDto(modelId = model.id!!, scopes = listOf("models:write"))
                            )
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.apiKey.mode").value(ApiKeyModes.GRANTS))
            .andExpect(jsonPath("$.apiKey.grants.length()").value(1))
            .andExpect(jsonPath("$.apiKey.grants[0].scopes.length()").value(2))
            .andExpect(jsonPath("$.apiKey.grants[0].scopes[?(@=='models:read')]").isNotEmpty)
            .andExpect(jsonPath("$.apiKey.grants[0].scopes[?(@=='models:write')]").isNotEmpty)
            .andReturn()

        val created = objectMapper.readValue<CreateApiKeyResponse>(result.response.contentAsString)
        val exchangeResult = mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.grants[0].scopes[?(@=='models:read')]").isNotEmpty)
            .andExpect(jsonPath("$.grants[0].scopes[?(@=='models:write')]").isNotEmpty)
            .andReturn()

        val exchange = objectMapper.readValue<ExchangeApiKeyResponse>(exchangeResult.response.contentAsString)
        assertTrue(exchange.grants!!.single().scopes.contains("models:read"))
        assertTrue(exchange.grants.single().scopes.contains("models:write"))
    }

    @Test
    fun `grants mode rejects duplicate modelId`() {
        val userId = registerAndGetUserId("apikey-grants-duplicate@test.com")
        val model = persistModel(userId, "grants-duplicate")

        mockMvc.perform(
            post("/api/v1/api-keys")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateApiKeyRequest(
                            name = "duplicate-grants",
                            mode = ApiKeyModes.GRANTS,
                            grants = listOf(
                                ApiKeyGrantDto(modelId = model.id!!, scopes = listOf("models:read")),
                                ApiKeyGrantDto(modelId = model.id!!, scopes = listOf("models:write"))
                            )
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("grants must have distinct modelIds"))
    }

    @Test
    fun `grants mode rejects more than 50 grants`() {
        val userId = registerAndGetUserId("apikey-grants-limit@test.com")
        val grants = (1..51).map { index ->
            val model = persistModel(userId, "grants-limit-$index")
            ApiKeyGrantDto(modelId = model.id!!, scopes = listOf("models:read"))
        }

        mockMvc.perform(
            post("/api/v1/api-keys")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateApiKeyRequest(
                            name = "too-many-grants",
                            mode = ApiKeyModes.GRANTS,
                            grants = grants
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("At most 50 grants are allowed"))
    }

    @Test
    fun `grants mode rejects unknown modelId`() {
        val userId = registerAndGetUserId("apikey-grants-unknown-model@test.com")
        val unknownModelId = UUID.randomUUID()

        mockMvc.perform(
            post("/api/v1/api-keys")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateApiKeyRequest(
                            name = "unknown-model-grant",
                            mode = ApiKeyModes.GRANTS,
                            grants = listOf(
                                ApiKeyGrantDto(modelId = unknownModelId, scopes = listOf("models:read"))
                            )
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Model $unknownModelId not found or not accessible"))
    }

    @Test
    fun `patch renames api key without changing mode or grants`() {
        val userId = registerAndGetUserId("apikey-patch-immutable@test.com")
        val model = persistModel(userId, "patch-immutable")
        val created = createKey(
            userId,
            grants = listOf(
                ApiKeyGrantDto(modelId = model.id!!, scopes = listOf("models:read"))
            )
        )

        mockMvc.perform(
            patch("/api/v1/api-keys/${created.apiKey.id}")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateApiKeyRequest(name = "renamed-grants-key")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("renamed-grants-key"))
            .andExpect(jsonPath("$.mode").value(ApiKeyModes.GRANTS))
            .andExpect(jsonPath("$.grants.length()").value(1))
            .andExpect(jsonPath("$.grants[0].modelId").value(model.id.toString()))
            .andExpect(jsonPath("$.grants[0].scopes[0]").value("models:read"))

        mockMvc.perform(get("/api/v1/api-keys").withAuth(userId, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].name").value("renamed-grants-key"))
            .andExpect(jsonPath("$.items[0].mode").value(ApiKeyModes.GRANTS))
    }

    @Test
    fun `deactivated user cannot exchange but key stays active until reactivation`() {
        val userId = registerAndGetUserId("apikey-deactivated-user@test.com")
        val created = createKey(userId, listOf("models:read"))

        val user = usersRepository.findById(userId).orElseThrow()
        user.isActive = false
        usersRepository.save(user)

        mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("User is inactive"))

        val storedKey = apiKeysRepository.findById(created.apiKey.id).orElseThrow()
        assertTrue(storedKey.revokedAt == null)

        user.isActive = true
        usersRepository.save(user)

        mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(created.key)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }
}
