package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ModelTraceabilityControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var linksRepository: LinksRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var nodeType: NodeTypes
    private lateinit var primaryLinkType: LinkTypes
    private lateinit var secondaryLinkType: LinkTypes
    private lateinit var notation: Notations
    private var diagramVersionCounter = 0

    @BeforeEach
    fun setUp() {
        owner = saveUser(Role.USER)
        model = saveModel(owner)
        nodeType = saveNodeType(owner)
        primaryLinkType = saveLinkType(owner, "primary")
        secondaryLinkType = saveLinkType(owner, "secondary")
        notation = saveNotation(owner)
        diagramVersionCounter = 0
    }

    @Test
    fun `pages direct graph neighbors by direction type and stable link-node order`() {
        val center = saveNode(model, "center")
        val incomingPeer = saveNode(model, "incoming")
        val outgoingPeer = saveNode(model, "outgoing")
        val cyclePeer = saveNode(model, "cycle")
        val incoming = saveLink(model, incomingPeer, center, primaryLinkType)
        val outgoing = saveLink(model, center, outgoingPeer, primaryLinkType)
        val cycleOut = saveLink(model, center, cyclePeer, secondaryLinkType)
        val cycleIn = saveLink(model, cyclePeer, center, secondaryLinkType)
        val self = saveLink(model, center, center, primaryLinkType)

        graph(center.id!!, direction = "in")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.content[?(@.link.id == '${incoming.id}')].node.id").value(incomingPeer.id.toString()))
            .andExpect(jsonPath("$.content[?(@.link.id == '${cycleIn.id}')].node.id").value(cyclePeer.id.toString()))
            .andExpect(jsonPath("$.content[?(@.link.id == '${self.id}')].node.id").value(center.id.toString()))

        graph(center.id!!, direction = "out")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.content[?(@.link.id == '${outgoing.id}')].node.id").value(outgoingPeer.id.toString()))
            .andExpect(jsonPath("$.content[?(@.link.id == '${cycleOut.id}')].node.id").value(cyclePeer.id.toString()))
            .andExpect(jsonPath("$.content[?(@.link.id == '${self.id}')].node.id").value(center.id.toString()))

        val bothResult = graph(center.id!!, direction = "both")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(5))
            .andReturn()
        val bothRows = objectMapper.readTree(bothResult.response.contentAsString).path("content")
        val actualPairs = bothRows.map {
            it.path("link").path("id").asText() to it.path("node").path("id").asText()
        }
        assertEquals(actualPairs.sortedWith(compareBy<Pair<String, String>>({ it.first }, { it.second })), actualPairs)
        assertEquals(1, bothRows.count { it.path("link").path("id").asText() == self.id.toString() })
        assertTrue(bothRows.all { it.path("link").has("attrs") && it.path("node").has("attrs") })

        graph(center.id!!, direction = "both", linkTypeId = primaryLinkType.id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(3))

        val expectedIds = actualPairs.map(Pair<String, String>::first)
        graph(center.id!!, direction = "both", page = 0, size = 2)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].link.id").value(expectedIds[0]))
            .andExpect(jsonPath("$.content[1].link.id").value(expectedIds[1]))
            .andExpect(jsonPath("$.page.totalElements").value(5))
            .andExpect(jsonPath("$.page.size").value(2))
        graph(center.id!!, direction = "both", page = 1, size = 2)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].link.id").value(expectedIds[2]))
            .andExpect(jsonPath("$.content[1].link.id").value(expectedIds[3]))
    }

    @Test
    fun `bounds graph pages validates direction and hides foreign or missing nodes`() {
        val center = saveNode(model, "center")
        val foreignModel = saveModel(owner)
        val foreignNode = saveNode(foreignModel, "foreign")

        graph(center.id!!, direction = "both", size = 500)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(50))
        graph(center.id!!, direction = "sideways")
            .andExpect(status().isBadRequest)
        graph(UUID.randomUUID(), direction = "both")
            .andExpect(status().isNotFound)
        graph(foreignNode.id!!, direction = "both")
            .andExpect(status().isNotFound)
        graph(center.id!!, direction = "both", modelId = UUID.randomUUID())
            .andExpect(status().isNotFound)
        diagramReferences(UUID.randomUUID())
            .andExpect(status().isNotFound)
        diagramReferences(foreignNode.id!!)
            .andExpect(status().isNotFound)
    }

    @Test
    fun `checks graph and diagram reference ACL before scoped reads`() {
        val center = saveNode(model, "center")
        val peer = saveNode(model, "peer")
        saveLink(model, center, peer, primaryLinkType)
        saveDiagram(model, "visible", attrsFor(center.id!!))
        val viewer = saveUser(Role.USER)
        val admin = saveUser(Role.ADMIN)
        val stranger = saveUser(Role.USER)
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = Instant.now()
            )
        )

        listOf(owner to Role.USER, viewer to Role.USER, admin to Role.ADMIN).forEach { (user, role) ->
            graph(center.id!!, user = user, role = role)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.page.totalElements").value(1))
            diagramReferences(center.id!!, user = user, role = role)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.page.totalElements").value(1))
        }

        graph(UUID.randomUUID(), user = stranger)
            .andExpect(status().isForbidden)
        diagramReferences(UUID.randomUUID(), user = stranger)
            .andExpect(status().isForbidden)
    }

    @Test
    fun `returns only active model scoped exact-path diagram references as slim ordered pages`() {
        val target = saveNode(model, "target")
        val diagramNode = saveNode(model, "diagram-node")
        val alpha = saveDiagram(model, "Alpha", attrsFor(target.id!!), node = diagramNode)
        val beta = saveDiagram(model, "Beta", attrsFor(target.id!!))
        val sameName = saveDiagram(model, "Beta", attrsFor(target.id!!))
        saveDiagram(
            model,
            "nested-false-positive",
            """{"instances":{"nodes":[{"nested":{"modelNodeId":"${target.id}"}}]}}"""
        )
        saveDiagram(
            model,
            "wrong-array-false-positive",
            """{"instances":{"links":[{"modelNodeId":"${target.id}"}]}}"""
        )
        saveDiagram(model, "string-false-positive", """{"note":"${target.id}"}""")
        val deleted = saveDiagram(model, "deleted", attrsFor(target.id!!))
        deleted.deleted = true
        diagramsRepository.save(deleted)
        val foreignModel = saveModel(owner)
        saveDiagram(foreignModel, "foreign", attrsFor(target.id!!), targetNotation = saveNotation(owner))

        val response = diagramReferences(target.id!!, page = 0, size = 2)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.content[0].id").value(alpha.id.toString()))
            .andExpect(jsonPath("$.content[0].nodeId").value(diagramNode.id.toString()))
            .andExpect(jsonPath("$.content[1].name").value("Beta"))
            .andReturn()
        objectMapper.readTree(response.response.contentAsString).path("content").forEach(::assertSlimDiagram)

        val secondPage = diagramReferences(target.id!!, page = 1, size = 2)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andReturn()
        val returnedId = objectMapper.readTree(secondPage.response.contentAsString)
            .path("content")
            .first()
            .path("id")
            .asText()
        assertTrue(returnedId == beta.id.toString() || returnedId == sameName.id.toString())
        diagramReferences(target.id!!, size = 500)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(50))
    }

    private fun assertSlimDiagram(diagram: JsonNode) {
        val fields = diagram.fieldNames().asSequence().toSet()
        assertEquals(setOf("id", "name", "version", "notationId", "nodeId"), fields)
        assertFalse(diagram.has("attrs"))
    }

    private fun graph(
        nodeId: UUID,
        direction: String = "both",
        linkTypeId: UUID? = null,
        page: Int = 0,
        size: Int = 50,
        modelId: UUID = model.id!!,
        user: Users = owner,
        role: Role = user.role
    ) = mockMvc.perform(
        get("/api/v1/models/$modelId/graph/neighbors")
            .param("nodeId", nodeId.toString())
            .param("direction", direction)
            .apply { linkTypeId?.let { param("linkTypeId", it.toString()) } }
            .param("page", page.toString())
            .param("size", size.toString())
            .withAuth(user.id!!, role)
    )

    private fun diagramReferences(
        nodeId: UUID,
        page: Int = 0,
        size: Int = 50,
        user: Users = owner,
        role: Role = user.role
    ) = mockMvc.perform(
        get("/api/v1/models/${model.id}/diagram-references")
            .param("nodeId", nodeId.toString())
            .param("page", page.toString())
            .param("size", size.toString())
            .withAuth(user.id!!, role)
    )

    private fun saveUser(role: Role): Users = usersRepository.save(
        Users(
            email = "trace-${UUID.randomUUID()}@test.com",
            role = role,
            createdAt = Instant.now()
        )
    )

    private fun saveModel(modelOwner: Users): Models = modelsRepository.save(
        Models(
            name = "trace-model-${UUID.randomUUID()}",
            version = "1.0.0",
            owner = modelOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNodeType(typeOwner: Users): NodeTypes = nodeTypesRepository.save(
        NodeTypes(
            name = "trace-node-type-${UUID.randomUUID()}",
            owner = typeOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveLinkType(typeOwner: Users, prefix: String): LinkTypes = linkTypesRepository.save(
        LinkTypes(
            name = "$prefix-${UUID.randomUUID()}",
            owner = typeOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNotation(notationOwner: Users): Notations = notationsRepository.save(
        Notations(
            name = "trace-notation-${UUID.randomUUID()}",
            version = "1.0.0",
            owner = notationOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNode(targetModel: Models, name: String): Nodes = nodesRepository.save(
        Nodes(
            stableId = UUID.randomUUID(),
            name = name,
            model = targetModel,
            owner = targetModel.owner,
            nodeType = nodeType,
            createdAt = Instant.now(),
            attrs = """{"label":"$name"}"""
        )
    )

    private fun saveLink(
        targetModel: Models,
        source: Nodes,
        target: Nodes,
        type: LinkTypes
    ): Links = linksRepository.save(
        Links(
            stableId = UUID.randomUUID(),
            model = targetModel,
            owner = targetModel.owner,
            linkType = type,
            source = source,
            target = target,
            createdAt = Instant.now(),
            attrs = """{"trace":true}"""
        )
    )

    private fun saveDiagram(
        targetModel: Models,
        name: String,
        attrs: String,
        node: Nodes? = null,
        targetNotation: Notations = notation
    ): Diagrams = diagramsRepository.save(
        Diagrams(
            name = name,
            version = "1.0.${diagramVersionCounter++}",
            owner = targetModel.owner,
            model = targetModel,
            notation = targetNotation,
            node = node,
            attrs = attrs,
            createdAt = Instant.now()
        )
    )

    private fun attrsFor(nodeId: UUID): String =
        """{"instances":{"nodes":[{"modelNodeId":"$nodeId"}]}}"""
}
