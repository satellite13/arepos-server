package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.apikey.ApiKeyGrantDto
import ru.kavader.arepos.dto.apikey.ApiKeyModes
import ru.kavader.arepos.dto.apikey.CreateApiKeyRequest
import ru.kavader.arepos.dto.apikey.CreateApiKeyResponse
import ru.kavader.arepos.dto.apikey.ExchangeApiKeyRequest
import ru.kavader.arepos.dto.apikey.ExchangeApiKeyResponse
import ru.kavader.arepos.dto.auth.RegisterRequest
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

/**
 * Pins the REST contract that the warchi-mcp client depends on:
 * envelope shapes, error code bodies and MCP token scopes.
 * If an assertion breaks, check lmru-warchi-mcp before changing either side.
 */
@SpringBootTest
@AutoConfigureMockMvc
class McpApiContractTest : ControllerIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var usersRepository: UsersRepository
    @Autowired lateinit var modelsRepository: ModelsRepository
    @Autowired lateinit var notationsRepository: NotationsRepository
    @Autowired lateinit var nodeTypesRepository: NodeTypesRepository
    @Autowired lateinit var linksRepository: LinksRepository
    @Autowired lateinit var linkTypesRepository: LinkTypesRepository
    @Autowired lateinit var nodesRepository: NodesRepository
    @Autowired lateinit var diagramsRepository: DiagramsRepository

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var notation: Notations
    private lateinit var nodeType: NodeTypes
    private lateinit var linkType: LinkTypes

    private fun setUpOwnerModel() {
        owner = usersRepository.save(
            Users(
                email = "mcp-contract-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        model = modelsRepository.save(
            Models(
                name = "mcp-contract-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        notation = notationsRepository.save(
            Notations(
                name = "MCP Contract Notation",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        nodeType = nodeTypesRepository.save(
            NodeTypes(name = "MCP Component", owner = owner, createdAt = Instant.now())
        )
        linkType = linkTypesRepository.save(
            LinkTypes(name = "MCP Relation", owner = owner, createdAt = Instant.now())
        )
    }

    @Test
    fun `list models uses items envelope expected by mcp list_models`() {
        setUpOwnerModel()

        mockMvc.perform(
            get("/api/v1/models").withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(model.id.toString()))
            .andExpect(jsonPath("$.items[0].name").value(model.name))
            .andExpect(jsonPath("$.items[0].version").value("1.0.0"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").isNumber)
    }

    @Test
    fun `list diagrams nodes and links use compact page envelope`() {
        setUpOwnerModel()
        val node = nodesRepository.save(persistedNode("Contract node"))
        val link = linksRepository.save(
            Links(
                stableId = UUID.randomUUID(),
                source = node,
                target = node,
                model = model,
                owner = owner,
                linkType = linkType,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/diagrams").param("modelId", model.id.toString()).withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.page.totalElements").value(0))
            .andExpect(jsonPath("$.page.number").value(0))
            .andExpect(jsonPath("$.page.size").isNumber)

        mockMvc.perform(
            get("/api/v1/nodes").param("modelId", model.id.toString()).withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(node.id.toString()))
            .andExpect(jsonPath("$.page.totalElements").value(1))

        mockMvc.perform(
            get("/api/v1/links").param("modelId", model.id.toString()).withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(link.id.toString()))
            .andExpect(jsonPath("$.page.totalElements").value(1))
    }

    @Test
    fun `unknown model returns structured error envelope with code and traceId`() {
        setUpOwnerModel()

        mockMvc.perform(
            get("/api/v1/models/${UUID.randomUUID()}").withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").isNotEmpty)
            .andExpect(jsonPath("$.traceId").isNotEmpty)
    }

    @Test
    fun `batch save conflict body carries error code expected by mcp classifier`() {
        setUpOwnerModel()
        val baseTime = Instant.parse("2024-06-01T10:00:00Z")
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "contract-node",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = baseTime
            )
        )

        val payload = mapOf(
            "nodes" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to node.id.toString(),
                        "name" to "contract-node-2",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to null,
                        "baseUpdatedAt" to "2024-06-01T09:00:00Z"
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("BATCH_SAVE_CONFLICT"))
            .andExpect(jsonPath("$.conflicts[0].kind").value("node"))
            .andExpect(jsonPath("$.conflicts[0].id").value(node.id.toString()))
            .andExpect(jsonPath("$.conflicts[0].serverUpdatedAt").isNotEmpty)
    }

    @Test
    fun `grants token forbidden body carries model_not_allowed reason`() {
        setUpOwnerModel()
        val denied = modelsRepository.save(
            Models(
                name = "mcp-contract-denied-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        val created = createApiKey(
            owner.id!!,
            grants = listOf(ApiKeyGrantDto(modelId = model.id!!, scopes = listOf("models:read")))
        )
        val token = exchangeApiKey(created.key)

        mockMvc.perform(
            get("/api/v1/models/${denied.id}").header("Authorization", "Bearer $token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("model_not_allowed")))
    }

    @Test
    fun `catalog search returns slim hits without attrs expected by mcp search_catalog`() {
        setUpOwnerModel()

        mockMvc.perform(
            get("/api/v1/search/catalog")
                .param("q", "mcp-contract-")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits.length()").value(1))
            .andExpect(jsonPath("$.hits[0].kind").value("model"))
            .andExpect(jsonPath("$.hits[0].id").value(model.id.toString()))
            .andExpect(jsonPath("$.hits[0].name").value(model.name))
            .andExpect(jsonPath("$.hits[0].version").value("1.0.0"))
            .andExpect(jsonPath("$.hits[0].attrs").doesNotExist())
    }

    private fun persistedNode(name: String): Nodes =
        Nodes(
            stableId = UUID.randomUUID(),
            name = name,
            model = model,
            owner = owner,
            nodeType = nodeType,
            parentNode = null,
            attrs = null,
            createdAt = Instant.now()
        )

    private fun createApiKey(userId: UUID, grants: List<ApiKeyGrantDto>): CreateApiKeyResponse {
        val result = mockMvc.perform(
            post("/api/v1/api-keys")
                .withAuth(userId, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateApiKeyRequest(
                            name = "mcp-contract-key",
                            mode = ApiKeyModes.GRANTS,
                            scopes = null,
                            grants = grants
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.key").isNotEmpty)
            .andReturn()
        return objectMapper.readValue(result.response.contentAsString)
    }

    private fun exchangeApiKey(key: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ExchangeApiKeyRequest(key)))
        )
            .andExpect(status().isOk)
            .andReturn()
        val exchange = objectMapper.readValue<ExchangeApiKeyResponse>(result.response.contentAsString)
        return exchange.accessToken
    }
}
