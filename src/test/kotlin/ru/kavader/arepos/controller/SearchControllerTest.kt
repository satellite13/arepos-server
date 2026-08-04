package ru.kavader.arepos.controller

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

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var linksRepository: LinksRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    private lateinit var owner: Users
    private lateinit var stranger: Users
    private lateinit var model: Models
    private lateinit var notation: Notations
    private lateinit var nodeType: NodeTypes
    private lateinit var linkType: LinkTypes

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            Users(
                email = "search-owner-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        stranger = usersRepository.save(
            Users(
                email = "search-stranger-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        model = modelsRepository.save(
            Models(
                name = "LemanaPro",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        notation = notationsRepository.save(
            Notations(
                name = "ArchiMate Search",
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
    }

    @Test
    fun `catalog search returns slim model hits`() {
        mockMvc.perform(
            get("/api/v1/search/catalog")
                .param("q", "lema")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.q").value("lema"))
            .andExpect(jsonPath("$.hits[0].kind").value("model"))
            .andExpect(jsonPath("$.hits[0].name").value("LemanaPro"))
            .andExpect(jsonPath("$.hits[0].version").value("1.0.0"))
            .andExpect(jsonPath("$.hits[0].id").value(model.id.toString()))
            .andExpect(jsonPath("$.hits[0].attrs").doesNotExist())
    }

    @Test
    fun `catalog search can filter notations only`() {
        mockMvc.perform(
            get("/api/v1/search/catalog")
                .param("q", "archi")
                .param("kinds", "notations")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits.length()").value(1))
            .andExpect(jsonPath("$.hits[0].kind").value("notation"))
            .andExpect(jsonPath("$.hits[0].name").value("ArchiMate Search"))
    }

    @Test
    fun `rejects blank q`() {
        mockMvc.perform(
            get("/api/v1/search/catalog")
                .param("q", "  ")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects unknown kinds`() {
        mockMvc.perform(
            get("/api/v1/search/catalog")
                .param("q", "lema")
                .param("kinds", "widgets")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `caps limit at 50`() {
        mockMvc.perform(
            get("/api/v1/search/catalog")
                .param("q", "lema")
                .param("limit", "100")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.limit").value(50))
    }

    @Test
    fun `model search finds node by partial name`() {
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "CRM System",
                createdAt = Instant.now(),
                attrs = """{"x":1}""",
                model = model,
                owner = owner,
                nodeType = nodeType
            )
        )

        mockMvc.perform(
            get("/api/v1/search/models/${model.id}")
                .param("q", "crm")
                .param("kinds", "nodes")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits.length()").value(1))
            .andExpect(jsonPath("$.hits[0].kind").value("node"))
            .andExpect(jsonPath("$.hits[0].id").value(node.id.toString()))
            .andExpect(jsonPath("$.hits[0].name").value("CRM System"))
            .andExpect(jsonPath("$.hits[0].typeName").value("Application Component"))
            .andExpect(jsonPath("$.hits[0].attrs").doesNotExist())
    }

    @Test
    fun `model search finds link by endpoint node name`() {
        val source = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Billing Service",
                createdAt = Instant.now(),
                model = model,
                owner = owner,
                nodeType = nodeType
            )
        )
        val target = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Payment DB",
                createdAt = Instant.now(),
                model = model,
                owner = owner,
                nodeType = nodeType
            )
        )
        val link = linksRepository.save(
            Links(
                stableId = UUID.randomUUID(),
                source = source,
                target = target,
                createdAt = Instant.now(),
                owner = owner,
                linkType = linkType,
                model = model
            )
        )

        mockMvc.perform(
            get("/api/v1/search/models/${model.id}")
                .param("q", "billing")
                .param("kinds", "links")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits.length()").value(1))
            .andExpect(jsonPath("$.hits[0].kind").value("link"))
            .andExpect(jsonPath("$.hits[0].id").value(link.id.toString()))
            .andExpect(jsonPath("$.hits[0].sourceName").value("Billing Service"))
            .andExpect(jsonPath("$.hits[0].targetName").value("Payment DB"))
            .andExpect(jsonPath("$.hits[0].typeName").value("Serving"))
    }

    @Test
    fun `model search finds diagram by name`() {
        val diagram = diagramsRepository.save(
            Diagrams(
                name = "CRM context",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation
            )
        )

        mockMvc.perform(
            get("/api/v1/search/models/${model.id}")
                .param("q", "context")
                .param("kinds", "diagrams")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits[0].kind").value("diagram"))
            .andExpect(jsonPath("$.hits[0].id").value(diagram.id.toString()))
            .andExpect(jsonPath("$.hits[0].notationName").value("ArchiMate Search"))
    }

    @Test
    fun `model search forbidden for other user`() {
        mockMvc.perform(
            get("/api/v1/search/models/${model.id}")
                .param("q", "crm")
                .withAuth(stranger.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `catalog hides models from other users`() {
        mockMvc.perform(
            get("/api/v1/search/catalog")
                .param("q", "lema")
                .param("kinds", "models")
                .withAuth(stranger.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits.length()").value(0))
    }
}
