package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
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
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class McpDiagramConvenienceControllerTest : ControllerIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var usersRepository: UsersRepository
    @Autowired lateinit var modelsRepository: ModelsRepository
    @Autowired lateinit var notationsRepository: NotationsRepository
    @Autowired lateinit var nodeTypesRepository: NodeTypesRepository
    @Autowired lateinit var linkTypesRepository: LinkTypesRepository
    @Autowired lateinit var componentsRepository: ComponentsRepository
    @Autowired lateinit var relationsRepository: RelationsRepository
    @Autowired lateinit var nodesRepository: NodesRepository
    @Autowired lateinit var linksRepository: LinksRepository
    @Autowired lateinit var diagramsRepository: DiagramsRepository

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var notation: Notations
    private lateinit var nodeType: NodeTypes
    private lateinit var linkType: LinkTypes
    private lateinit var component: Components
    private lateinit var relation: Relations

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            Users(
                email = "conv-owner-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        model = modelsRepository.save(
            Models(
                name = "conv-model-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        notation = notationsRepository.save(
            Notations(
                name = "ArchiMate Conv",
                createdAt = Instant.now(),
                version = "3.2.0",
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
        linkType = linkTypesRepository.save(
            LinkTypes(
                name = "Serving",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        component = componentsRepository.save(
            Components(
                name = "Application Component",
                attrs = null,
                createdAt = Instant.now(),
                version = "1.0.0",
                notation = notation,
                owner = owner,
                nodeType = nodeType
            )
        )
        relation = relationsRepository.save(
            Relations(
                attrs = null,
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner,
                notation = notation,
                name = "Serving",
                linkType = linkType
            )
        )
    }

    @Test
    fun `creates node by componentName and writes notationComponents`() {
        mockMvc.perform(
            post("/api/v1/nodes")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "CRM",
                      "modelId": "${model.id}",
                      "notationId": "${notation.id}",
                      "componentName": "Application Component"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.nodeTypeId").value(nodeType.id.toString()))
            .andExpect(jsonPath("$.attrs").value(org.hamcrest.Matchers.containsString(component.id.toString())))
            .andExpect(jsonPath("$.attrs").value(org.hamcrest.Matchers.containsString("notationComponents")))
    }

    @Test
    fun `ambiguous componentName returns AMBIGUOUS_NOTATION_ELEMENT`() {
        componentsRepository.save(
            Components(
                name = "Application Component",
                attrs = null,
                createdAt = Instant.now(),
                version = "2.0.0",
                notation = notation,
                owner = owner,
                nodeType = nodeType
            )
        )

        mockMvc.perform(
            post("/api/v1/nodes")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "CRM",
                      "modelId": "${model.id}",
                      "notationId": "${notation.id}",
                      "componentName": "Application Component"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("AMBIGUOUS_NOTATION_ELEMENT"))
            .andExpect(jsonPath("$.candidates.length()").value(2))
    }

    @Test
    fun `creates link by relationName and writes notationRelations`() {
        val source = persistNode("A")
        val target = persistNode("B")

        mockMvc.perform(
            post("/api/v1/links")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "modelId": "${model.id}",
                      "sourceId": "${source.id}",
                      "targetId": "${target.id}",
                      "notationId": "${notation.id}",
                      "relationName": "Serving"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.linkTypeId").value(linkType.id.toString()))
            .andExpect(jsonPath("$.attrs").value(org.hamcrest.Matchers.containsString(relation.id.toString())))
    }

    @Test
    fun `ensureLink is idempotent`() {
        val source = persistNode("Src")
        val target = persistNode("Tgt")
        val body =
            """
            {
              "modelId": "${model.id}",
              "sourceId": "${source.id}",
              "targetId": "${target.id}",
              "notationId": "${notation.id}",
              "relationName": "Serving"
            }
            """.trimIndent()

        val first = mockMvc.perform(
            post("/api/v1/links/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(true))
            .andReturn()

        val linkId = objectMapper.readTree(first.response.contentAsString).path("link").path("id").asText()

        mockMvc.perform(
            post("/api/v1/links/ensure")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(false))
            .andExpect(jsonPath("$.link.id").value(linkId))

        assertEquals(1, linksRepository.count())
    }

    @Test
    fun `merge instances upserts nodes and resolves edges by modelLinkId`() {
        val n1 = persistNode("N1", attrs = """{"notationComponents":{"${notation.id}":{"componentId":"${component.id}"}}}""")
        val n2 = persistNode("N2")
        val link = linksRepository.save(
            Links(
                stableId = UUID.randomUUID(),
                source = n1,
                target = n2,
                createdAt = Instant.now(),
                owner = owner,
                linkType = linkType,
                model = model
            )
        )
        val diagram = diagramsRepository.save(
            Diagrams(
                name = "D1",
                createdAt = Instant.now(),
                updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                attrs = """{"instances":{"nodes":[],"edges":[]}}""",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                node = null
            )
        )

        mockMvc.perform(
            post("/api/v1/diagrams/${diagram.id}/instances:merge")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nodes": [
                        {"modelNodeId":"${n1.id}","x":10,"y":20,"width":120,"height":60},
                        {"modelNodeId":"${n2.id}","x":200,"y":20}
                      ],
                      "edges": [
                        {"modelLinkId":"${link.id}"}
                      ]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.counts.nodesAdded").value(2))
            .andExpect(jsonPath("$.counts.edgesAdded").value(1))
            .andExpect(jsonPath("$.diagram.attrs").value(org.hamcrest.Matchers.containsString(n1.id.toString())))
            .andExpect(jsonPath("$.diagram.attrs").value(org.hamcrest.Matchers.containsString(link.id.toString())))
            .andExpect(jsonPath("$.diagram.attrs").value(org.hamcrest.Matchers.containsString("notationComponentId")))

        // update geometry for existing instance
        mockMvc.perform(
            post("/api/v1/diagrams/${diagram.id}/instances:merge")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nodes": [
                        {"modelNodeId":"${n1.id}","x":99,"y":88}
                      ]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.counts.nodesUpdated").value(1))
            .andExpect(jsonPath("$.counts.nodesAdded").value(0))

        val attrs = diagramsRepository.findById(diagram.id!!).get().attrs!!
        val x = objectMapper.readTree(attrs).path("instances").path("nodes")
            .first { it.path("modelNodeId").asText() == n1.id.toString() }
            .path("x").asDouble()
        assertEquals(99.0, x)
        assertTrue(attrs.contains(n2.id.toString()))
    }

    @Test
    fun `merge with stale baseUpdatedAt returns DIAGRAM_CONFLICT`() {
        val n1 = persistNode("Only")
        val diagram = diagramsRepository.save(
            Diagrams(
                name = "D-conflict",
                createdAt = Instant.now(),
                updatedAt = Instant.parse("2026-02-01T00:00:00Z"),
                attrs = """{"instances":{"nodes":[],"edges":[]}}""",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                node = null
            )
        )

        mockMvc.perform(
            post("/api/v1/diagrams/${diagram.id}/instances:merge")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "baseUpdatedAt": "2026-01-01T00:00:00Z",
                      "nodes": [{"modelNodeId":"${n1.id}","x":1,"y":1}]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("DIAGRAM_CONFLICT"))
    }

    @Test
    fun `merge edge without node instances returns 400`() {
        val n1 = persistNode("S")
        val n2 = persistNode("T")
        val link = linksRepository.save(
            Links(
                stableId = UUID.randomUUID(),
                source = n1,
                target = n2,
                createdAt = Instant.now(),
                owner = owner,
                linkType = linkType,
                model = model
            )
        )
        val diagram = diagramsRepository.save(
            Diagrams(
                name = "D-empty",
                createdAt = Instant.now(),
                attrs = """{"instances":{"nodes":[],"edges":[]}}""",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                node = null
            )
        )

        mockMvc.perform(
            post("/api/v1/diagrams/${diagram.id}/instances:merge")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "edges": [{"modelLinkId":"${link.id}"}]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `search notation returns slim component and relation hits`() {
        mockMvc.perform(
            get("/api/v1/search/notations/${notation.id}")
                .param("q", "serv")
                .param("kinds", "relations")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits.length()").value(1))
            .andExpect(jsonPath("$.hits[0].kind").value("relation"))
            .andExpect(jsonPath("$.hits[0].name").value("Serving"))
            .andExpect(jsonPath("$.hits[0].linkTypeId").value(linkType.id.toString()))
            .andExpect(jsonPath("$.hits[0].attrs").doesNotExist())

        mockMvc.perform(
            get("/api/v1/search/notations/${notation.id}")
                .param("q", "application")
                .param("kinds", "components")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits[0].kind").value("component"))
            .andExpect(jsonPath("$.hits[0].nodeTypeId").value(nodeType.id.toString()))
    }

    private fun persistNode(name: String, attrs: String? = null): Nodes =
        nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = name,
                createdAt = Instant.now(),
                attrs = attrs,
                model = model,
                owner = owner,
                nodeType = nodeType
            )
        )
}
