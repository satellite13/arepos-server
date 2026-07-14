package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ModelDiffControllerTest : ControllerIntegrationTest() {

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
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Test
    fun `owner compares model versions with changed nodes`() {
        val fixture = createVersionsFixture()

        mockMvc.perform(
            get("/api/v1/models/${fixture.base.id}/diff/${fixture.target.id}")
                .withAuth(fixture.owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodes[0].kind").value("modified"))
            .andExpect(jsonPath("$.nodes[0].path").value("Target node"))
            .andExpect(jsonPath("$.nodes[0].base.name").value("Base node"))
            .andExpect(jsonPath("$.nodes[0].target.name").value("Target node"))
            .andExpect(jsonPath("$.links").isArray)
            .andExpect(jsonPath("$.diagrams").isArray)
    }

    @Test
    fun `diff returns 404 when base or target model is missing`() {
        val fixture = createVersionsFixture()
        val missingId = UUID.randomUUID()

        mockMvc.perform(
            get("/api/v1/models/$missingId/diff/${fixture.target.id}")
                .withAuth(fixture.owner.id!!, Role.USER)
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            get("/api/v1/models/${fixture.base.id}/diff/$missingId")
                .withAuth(fixture.owner.id!!, Role.USER)
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `outsider cannot compare unshared model versions`() {
        val fixture = createVersionsFixture()
        val outsider = persistUser("model-diff-outsider@test.com")

        mockMvc.perform(
            get("/api/v1/models/${fixture.base.id}/diff/${fixture.target.id}")
                .withAuth(outsider.id!!, Role.USER)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `grantee with view share only on base cannot compare model versions`() {
        val fixture = createVersionsFixture()
        val grantee = persistUser("model-diff-base-only-grantee@test.com")
        grantView(fixture.owner, grantee, fixture.base)

        mockMvc.perform(
            get("/api/v1/models/${fixture.base.id}/diff/${fixture.target.id}")
                .withAuth(grantee.id!!, Role.USER)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `grantee with view share only on target cannot compare model versions`() {
        val fixture = createVersionsFixture()
        val grantee = persistUser("model-diff-target-only-grantee@test.com")
        grantView(fixture.owner, grantee, fixture.target)

        mockMvc.perform(
            get("/api/v1/models/${fixture.base.id}/diff/${fixture.target.id}")
                .withAuth(grantee.id!!, Role.USER)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `grantee with view shares compares both model versions`() {
        val fixture = createVersionsFixture()
        val grantee = persistUser("model-diff-grantee@test.com")
        grantView(fixture.owner, grantee, fixture.base)
        grantView(fixture.owner, grantee, fixture.target)

        mockMvc.perform(
            get("/api/v1/models/${fixture.base.id}/diff/${fixture.target.id}")
                .withAuth(grantee.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodes[0].kind").value("modified"))
    }

    private fun createVersionsFixture(): ModelVersionsFixture {
        val owner = persistUser("model-diff-owner-${UUID.randomUUID()}@test.com")
        val base = persistModel(owner, "Comparison model", "1.0.0")
        val target = persistModel(owner, "Comparison model", "1.1.0", base)
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "model-diff-node-type-${UUID.randomUUID()}",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        val stableId = UUID.randomUUID()
        nodesRepository.save(
            Nodes(
                stableId = stableId,
                name = "Base node",
                model = base,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        nodesRepository.save(
            Nodes(
                stableId = stableId,
                name = "Target node",
                model = target,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        return ModelVersionsFixture(owner, base, target)
    }

    private fun persistUser(email: String): Users =
        usersRepository.save(
            Users(
                email = email,
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

    private fun persistModel(owner: Users, name: String, version: String, source: Models? = null): Models =
        modelsRepository.save(
            Models(
                name = name,
                version = version,
                owner = owner,
                source = source,
                createdAt = Instant.now()
            )
        )

    private fun grantView(owner: Users, grantee: Users, model: Models) {
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = grantee,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = Instant.now()
            )
        )
    }

    private data class ModelVersionsFixture(
        val owner: Users,
        val base: Models,
        val target: Models
    )
}
