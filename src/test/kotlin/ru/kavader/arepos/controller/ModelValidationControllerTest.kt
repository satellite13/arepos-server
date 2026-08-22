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
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ModelValidationControllerTest : ControllerIntegrationTest() {

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

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var directoryType: NodeTypes
    private lateinit var applicationComponentType: NodeTypes
    private lateinit var servingType: LinkTypes

    @BeforeEach
    fun setUp() {
        owner = saveUser(Role.USER)
        model = saveModel(owner)
        directoryType = saveNodeType(owner, "Directory")
        applicationComponentType = saveNodeType(owner, "Application Component")
        servingType = saveLinkType(owner, "Serving")
    }

    @Test
    fun `groups nodes by type and case-insensitive trimmed name and skips directory`() {
        val apps = saveNode(model, "Apps", directoryType)
        saveNode(model, " apps ", directoryType)
        saveNode(model, "CRM", applicationComponentType, parent = apps)
        saveNode(model, " crm ", applicationComponentType, parent = apps)
        saveNode(model, "Other", applicationComponentType, parent = apps)

        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(owner.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicateNodes.length()").value(1))
            .andExpect(jsonPath("$.duplicateNodes[0].count").value(2))
            .andExpect(jsonPath("$.duplicateNodes[0].nodes[0].parentName").value("Apps"))
    }

    @Test
    fun `groups directed links and ignores reverse pair`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        saveLink(model, source, target, servingType)
        saveLink(model, source, target, servingType)
        saveLink(model, target, source, servingType)

        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(owner.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicateLinks.length()").value(1))
            .andExpect(jsonPath("$.duplicateLinks[0].count").value(2))
    }

    @Test
    fun `forbidden viewer gets 403`() {
        val viewer = saveUser(Role.USER)
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

        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(owner.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(viewer.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(stranger.id!!, Role.USER))
            .andExpect(status().isForbidden)
    }

    private fun saveUser(role: Role): Users = usersRepository.save(
        Users(
            email = "validation-${UUID.randomUUID()}@test.com",
            role = role,
            createdAt = Instant.now()
        )
    )

    private fun saveModel(modelOwner: Users): Models = modelsRepository.save(
        Models(
            name = "validation-model-${UUID.randomUUID()}",
            version = "1.0.0",
            owner = modelOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNodeType(typeOwner: Users, name: String): NodeTypes = nodeTypesRepository.save(
        NodeTypes(
            name = name,
            owner = typeOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveLinkType(typeOwner: Users, name: String): LinkTypes = linkTypesRepository.save(
        LinkTypes(
            name = name,
            owner = typeOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNode(
        targetModel: Models,
        name: String,
        type: NodeTypes,
        parent: Nodes? = null
    ): Nodes = nodesRepository.save(
        Nodes(
            stableId = UUID.randomUUID(),
            name = name,
            model = targetModel,
            owner = targetModel.owner,
            nodeType = type,
            parentNode = parent,
            createdAt = Instant.now()
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
            createdAt = Instant.now()
        )
    )
}
