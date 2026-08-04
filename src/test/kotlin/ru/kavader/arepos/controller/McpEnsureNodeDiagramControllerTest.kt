package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class McpEnsureNodeDiagramControllerTest : ControllerIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var usersRepository: UsersRepository
    @Autowired lateinit var modelsRepository: ModelsRepository
    @Autowired lateinit var notationsRepository: NotationsRepository
    @Autowired lateinit var nodeTypesRepository: NodeTypesRepository
    @Autowired lateinit var nodesRepository: NodesRepository
    @Autowired lateinit var diagramsRepository: DiagramsRepository

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var notation: Notations
    private lateinit var nodeType: NodeTypes

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            Users(
                email = "ensure-owner-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        model = modelsRepository.save(
            Models(
                name = "ensure-model-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        notation = notationsRepository.save(
            Notations(
                name = "Ensure Notation",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "Application Component",
                createdAt = Instant.now(),
                owner = owner
            )
        )
    }

    @Test
    fun `ensureNode creates then second call returns created false with same id`() {
        val body =
            """
            {
              "name": "CRM",
              "modelId": "${model.id}",
              "nodeTypeId": "${nodeType.id}"
            }
            """.trimIndent()

        val first = mockMvc.perform(
            post("/api/v1/nodes/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.node.name").value("CRM"))
            .andExpect(jsonPath("$.node.modelId").value(model.id.toString()))
            .andExpect(jsonPath("$.node.nodeTypeId").value(nodeType.id.toString()))
            .andReturn()

        val nodeId = objectMapper.readTree(first.response.contentAsString).path("node").path("id").asText()

        mockMvc.perform(
            post("/api/v1/nodes/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(false))
            .andExpect(jsonPath("$.node.id").value(nodeId))

        assertEquals(1, nodesRepository.count())
    }

    @Test
    fun `ensureNode same name under different parents creates two nodes and respects parent`() {
        val parentA = persistNode("Parent A")
        val parentB = persistNode("Parent B")

        val bodyUnderA =
            """
            {
              "name": "Shared",
              "modelId": "${model.id}",
              "nodeTypeId": "${nodeType.id}",
              "parentNodeId": "${parentA.id}"
            }
            """.trimIndent()

        val bodyUnderB =
            """
            {
              "name": "Shared",
              "modelId": "${model.id}",
              "nodeTypeId": "${nodeType.id}",
              "parentNodeId": "${parentB.id}"
            }
            """.trimIndent()

        val first = mockMvc.perform(
            post("/api/v1/nodes/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyUnderA)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.node.parentNodeId").value(parentA.id.toString()))
            .andReturn()

        val nodeAId = objectMapper.readTree(first.response.contentAsString).path("node").path("id").asText()

        val second = mockMvc.perform(
            post("/api/v1/nodes/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyUnderB)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.node.parentNodeId").value(parentB.id.toString()))
            .andReturn()

        val nodeBId = objectMapper.readTree(second.response.contentAsString).path("node").path("id").asText()
        assertNotEquals(nodeAId, nodeBId)
        assertEquals(4, nodesRepository.count())

        mockMvc.perform(
            post("/api/v1/nodes/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyUnderA)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(false))
            .andExpect(jsonPath("$.node.id").value(nodeAId))
            .andExpect(jsonPath("$.node.parentNodeId").value(parentA.id.toString()))
    }

    @Test
    fun `ensureNode ambiguous same parent and name returns AMBIGUOUS_NODE`() {
        val parent = persistNode("Parent")
        val dup1 = persistNode("Dup", parentNode = parent)
        val dup2 = persistNode("Dup", parentNode = parent)

        mockMvc.perform(
            post("/api/v1/nodes/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Dup",
                      "modelId": "${model.id}",
                      "nodeTypeId": "${nodeType.id}",
                      "parentNodeId": "${parent.id}"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("AMBIGUOUS_NODE"))
            .andExpect(jsonPath("$.candidates.length()").value(2))
            .andExpect(
                jsonPath("$.candidates[*].id").value(
                    org.hamcrest.Matchers.containsInAnyOrder(dup1.id.toString(), dup2.id.toString())
                )
            )
    }

    @Test
    fun `ensureDiagram creates with empty instances then second call returns created false`() {
        val body =
            """
            {
              "name": "Landscape",
              "modelId": "${model.id}",
              "notationId": "${notation.id}"
            }
            """.trimIndent()

        val first = mockMvc.perform(
            post("/api/v1/diagrams/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.diagram.name").value("Landscape"))
            .andExpect(jsonPath("$.diagram.modelId").value(model.id.toString()))
            .andExpect(jsonPath("$.diagram.notationId").value(notation.id.toString()))
            .andReturn()

        val firstBody = objectMapper.readTree(first.response.contentAsString)
        val diagramId = firstBody.path("diagram").path("id").asText()
        val instances = objectMapper.readTree(firstBody.path("diagram").path("attrs").asText()).path("instances")
        assertTrue(instances.path("nodes").isArray && instances.path("nodes").isEmpty)
        assertTrue(instances.path("edges").isArray && instances.path("edges").isEmpty)

        mockMvc.perform(
            post("/api/v1/diagrams/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(false))
            .andExpect(jsonPath("$.diagram.id").value(diagramId))

        assertEquals(1, diagramsRepository.count())
    }

    @Test
    fun `ensureDiagram with two versions same name returns latest`() {
        val older = diagramsRepository.save(
            Diagrams(
                name = "Multi-version",
                createdAt = Instant.now(),
                attrs = """{"instances":{"nodes":[],"edges":[]}}""",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                node = null
            )
        )
        val newer = diagramsRepository.save(
            Diagrams(
                name = "Multi-version",
                createdAt = Instant.now(),
                attrs = """{"instances":{"nodes":[],"edges":[]}}""",
                version = "1.1.0",
                owner = owner,
                model = model,
                notation = notation,
                node = null
            )
        )

        mockMvc.perform(
            post("/api/v1/diagrams/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Multi-version",
                      "modelId": "${model.id}",
                      "notationId": "${notation.id}"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(false))
            .andExpect(jsonPath("$.diagram.id").value(newer.id.toString()))
            .andExpect(jsonPath("$.diagram.version").value("1.1.0"))

        assertNotEquals(older.id, newer.id)
    }

    private fun persistNode(name: String, parentNode: Nodes? = null): Nodes =
        nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = name,
                createdAt = Instant.now(),
                attrs = null,
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = parentNode
            )
        )
}
