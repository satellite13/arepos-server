package ru.kavader.arepos.controller

import org.hamcrest.Matchers.hasItems
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @MockitoSpyBean
    lateinit var accessService: ResourceAccessService

    @BeforeEach
    fun setupCerbosMock() {
        doAnswer { CurrentUser.getRole() == "ADMIN" }
            .`when`(accessService)
            .canViewAdminPanel()
    }

    @Test
    fun `stats nodeTypes count reflects accessible types for non-admin`() {
        val currentUser = usersRepository.save(
            Users(
                email = "dashboard-stats-user@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val otherUser = usersRepository.save(
            Users(
                email = "dashboard-stats-other@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val now = Instant.now()
        nodeTypesRepository.saveAll(
            listOf(
                NodeTypes(name = "OwnType", createdAt = now, updatedAt = now, owner = currentUser),
                NodeTypes(name = "ForeignType", createdAt = now, updatedAt = now, owner = otherUser)
            )
        )

        mockMvc.perform(
            get("/api/v1/dashboard/stats")
                .withAuth(currentUser.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodeTypes").value(1))
    }

    @Test
    fun `stats nodeTypes count includes system Directory for admin`() {
        val admin = usersRepository.save(
            Users(
                email = "dashboard-stats-admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val now = Instant.now()
        nodeTypesRepository.saveAll(
            listOf(
                NodeTypes(
                    name = "Directory",
                    attrs = """{"system":{"hiddenTreeRootType":true}}""",
                    createdAt = now,
                    updatedAt = now,
                    owner = admin
                ),
                NodeTypes(name = "BusinessActor", createdAt = now, updatedAt = now, owner = admin)
            )
        )

        mockMvc.perform(
            get("/api/v1/dashboard/stats")
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodeTypes").value(2))
    }

    @Test
    fun `stats linkTypes count reflects accessible types for non-admin`() {
        val currentUser = usersRepository.save(
            Users(
                email = "dashboard-stats-link-user@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val otherUser = usersRepository.save(
            Users(
                email = "dashboard-stats-link-other@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val now = Instant.now()
        linkTypesRepository.saveAll(
            listOf(
                LinkTypes(name = "OwnLink", createdAt = now, updatedAt = now, owner = currentUser),
                LinkTypes(name = "ForeignLink", createdAt = now, updatedAt = now, owner = otherUser)
            )
        )

        mockMvc.perform(
            get("/api/v1/dashboard/stats")
                .withAuth(currentUser.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.linkTypes").value(1))
    }

    @Test
    fun `recent diagrams returns only accessible diagrams for non-admin`() {
        val currentUser = usersRepository.save(
            Users(email = "dashboard-diag-user@test.com", role = Role.USER, createdAt = Instant.now())
        )
        val otherUser = usersRepository.save(
            Users(email = "dashboard-diag-other@test.com", role = Role.USER, createdAt = Instant.now())
        )
        val ownModel = persistModel(currentUser, "OwnModel")
        val foreignModel = persistModel(otherUser, "ForeignModel")
        val sharedModel = persistModel(otherUser, "SharedModel")
        val ownNotation = persistNotation(currentUser, "OwnNotation")
        val otherNotation = persistNotation(otherUser, "OtherNotation")
        val now = Instant.now()
        val ownDiagram = persistDiagram(currentUser, ownModel, ownNotation, "OwnDiagram", now.minusSeconds(30))
        persistDiagram(otherUser, foreignModel, otherNotation, "ForeignDiagram", now.minusSeconds(20))
        val sharedDiagram = persistDiagram(otherUser, sharedModel, otherNotation, "SharedDiagram", now.minusSeconds(10))
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = sharedModel.id!!,
                granteeUser = currentUser,
                grantedByUser = otherUser,
                permission = SharePermission.VIEW,
                createdAt = now
            )
        )

        mockMvc.perform(
            get("/api/v1/dashboard/recent?limit=10")
                .withAuth(currentUser.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activity").doesNotExist())
            .andExpect(jsonPath("$.diagrams.length()").value(2))
            .andExpect(jsonPath("$.diagrams[*].id").value(hasItems(ownDiagram.id.toString(), sharedDiagram.id.toString())))
            .andExpect(jsonPath("$.diagrams[?(@.id == '${ownDiagram.id}')].modelId").value(ownModel.id.toString()))
            .andExpect(jsonPath("$.diagrams[?(@.id == '${ownDiagram.id}')].modelName").value("OwnModel"))
            .andExpect(jsonPath("$.diagrams[?(@.id == '${sharedDiagram.id}')].modelName").value("SharedModel"))
    }

    @Test
    fun `recent diagrams returns all diagrams for admin`() {
        val admin = usersRepository.save(
            Users(email = "dashboard-diag-admin@test.com", role = Role.ADMIN, createdAt = Instant.now())
        )
        val user = usersRepository.save(
            Users(email = "dashboard-diag-user2@test.com", role = Role.USER, createdAt = Instant.now())
        )
        val adminModel = persistModel(admin, "AdminModel")
        val userModel = persistModel(user, "UserModel")
        val adminNotation = persistNotation(admin, "AdminNotation")
        val userNotation = persistNotation(user, "UserNotation")
        val now = Instant.now()
        val adminDiagram = persistDiagram(admin, adminModel, adminNotation, "AdminDiagram", now.minusSeconds(20))
        val userDiagram = persistDiagram(user, userModel, userNotation, "UserDiagram", now.minusSeconds(10))

        mockMvc.perform(
            get("/api/v1/dashboard/recent?limit=10")
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activity").doesNotExist())
            .andExpect(
                jsonPath("$.diagrams[*].id").value(
                    hasItems(adminDiagram.id.toString(), userDiagram.id.toString())
                )
            )
    }

    private fun persistModel(owner: Users, name: String): Models {
        val now = Instant.now()
        return modelsRepository.save(
            Models(
                name = name,
                version = "1.0.0",
                owner = owner,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun persistNotation(owner: Users, name: String): Notations {
        val now = Instant.now()
        return notationsRepository.save(
            Notations(
                name = name,
                version = "1.0.0",
                owner = owner,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun persistDiagram(
        owner: Users,
        model: Models,
        notation: Notations,
        name: String,
        updatedAt: Instant
    ): Diagrams {
        val diagram = Diagrams(
            name = name,
            version = "1.0.0",
            owner = owner,
            model = model,
            notation = notation,
            createdAt = updatedAt,
            updatedAt = updatedAt
        )
        return diagramsRepository.save(diagram)
    }
}
