package ru.kavader.arepos.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["arepos.validation-report.max-groups=2"])
class ModelValidationReportLimitTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

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

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var nodeType: NodeTypes
    private lateinit var linkType: LinkTypes

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            Users(
                email = "validation-limit-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        model = modelsRepository.save(
            Models(
                name = "validation-limit-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        nodeType = nodeTypesRepository.save(
            NodeTypes(name = "Application Component", owner = owner, createdAt = Instant.now())
        )
        linkType = linkTypesRepository.save(
            LinkTypes(name = "Serving", owner = owner, createdAt = Instant.now())
        )
    }

    @Test
    fun `caps duplicate link groups and reports the full total`() {
        repeat(3) { index ->
            val source = saveNode("S$index")
            val target = saveNode("T$index")
            saveLink(source, target)
            saveLink(source, target)
        }

        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(owner.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicateLinks.length()").value(2))
            .andExpect(jsonPath("$.duplicateLinksTotal").value(3))
    }

    @Test
    fun `caps duplicate node groups and reports the full total`() {
        repeat(3) { index ->
            saveNode("CRM-$index")
            saveNode("CRM-$index")
        }

        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(owner.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicateNodes.length()").value(2))
            .andExpect(jsonPath("$.duplicateNodesTotal").value(3))
    }

    private fun saveNode(name: String): Nodes = nodesRepository.save(
        Nodes(
            stableId = UUID.randomUUID(),
            name = name,
            model = model,
            owner = owner,
            nodeType = nodeType,
            createdAt = Instant.now(),
            updatedAt = Instant.parse("2026-08-22T12:00:00Z")
        )
    )

    private fun saveLink(source: Nodes, target: Nodes): Links = linksRepository.save(
        Links(
            stableId = UUID.randomUUID(),
            model = model,
            owner = owner,
            linkType = linkType,
            source = source,
            target = target,
            createdAt = Instant.now(),
            updatedAt = Instant.parse("2026-08-22T12:00:00Z")
        )
    )
}
