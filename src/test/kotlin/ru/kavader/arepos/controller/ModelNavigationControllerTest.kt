package ru.kavader.arepos.controller

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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ModelNavigationControllerTest : ControllerIntegrationTest() {

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
    lateinit var resourceSharesRepository: ResourceSharesRepository

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var nodeType: NodeTypes

    @BeforeEach
    fun setUp() {
        owner = saveUser(Role.USER)
        model = saveModel(owner)
        nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "navigation-type-${UUID.randomUUID()}",
                owner = owner,
                createdAt = Instant.now()
            )
        )
    }

    @Test
    fun `returns configured-root ancestors from root child to direct parent`() {
        val hiddenRoot = saveNode("hidden-root", attrs = HIDDEN_ROOT_ATTRS)
        val rootChild = saveNode("root-child", parent = hiddenRoot)
        val directParent = saveNode("direct-parent", parent = rootChild)
        val target = saveNode("target", parent = directParent)
        configureRoot(hiddenRoot)

        val result = ancestors(model.id!!, target.id!!, owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(rootChild.id.toString()))
            .andExpect(jsonPath("$[0].hasChildren").value(true))
            .andExpect(jsonPath("$[1].id").value(directParent.id.toString()))
            .andExpect(jsonPath("$[1].hasChildren").value(true))
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn()

        assertEquals(
            listOf(rootChild.id.toString(), directParent.id.toString()),
            objectMapper.readTree(result.response.contentAsString).map { it.path("id").asText() }
        )
    }

    @Test
    fun `supports legacy null root and excludes a hidden legacy root`() {
        val visibleRoot = saveNode("visible-root")
        val visibleParent = saveNode("visible-parent", parent = visibleRoot)
        val target = saveNode("target", parent = visibleParent)

        ancestors(model.id!!, target.id!!, owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(visibleRoot.id.toString()))
            .andExpect(jsonPath("$[1].id").value(visibleParent.id.toString()))

        val hiddenLegacyRoot = saveNode("hidden-legacy-root", attrs = HIDDEN_ROOT_ATTRS)
        val hiddenRootChild = saveNode("hidden-root-child", parent = hiddenLegacyRoot)
        val hiddenTarget = saveNode("hidden-target", parent = hiddenRootChild)

        ancestors(model.id!!, hiddenTarget.id!!, owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(hiddenRootChild.id.toString()))
    }

    @Test
    fun `returns empty ancestors for the hidden root itself`() {
        val hiddenRoot = saveNode("hidden-root", attrs = HIDDEN_ROOT_ATTRS)
        configureRoot(hiddenRoot)

        ancestors(model.id!!, hiddenRoot.id!!, owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `accepts depth 256 and rejects depth 257`() {
        val allowed = insertChain(ancestorCount = 256)
        val allowedResult = ancestors(model.id!!, allowed.last(), owner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(256))
            .andReturn()
        val allowedIds = objectMapper.readTree(allowedResult.response.contentAsString)
            .map { UUID.fromString(it.path("id").asText()) }
        assertEquals(allowed.dropLast(1), allowedIds)

        val overflowModel = saveModel(owner)
        val overflow = insertChain(ancestorCount = 257, targetModel = overflowModel)
        ancestors(overflowModel.id!!, overflow.last(), owner)
            .andExpect(status().isConflict)
    }

    @Test
    fun `rejects cycles without unbounded traversal`() {
        val first = saveNode("first")
        val second = saveNode("second", parent = first)
        val target = saveNode("target", parent = second)
        withoutCycleTrigger {
            jdbcTemplate.update("UPDATE nodes SET parent_node = ? WHERE id = ?", second.id, first.id)
        }

        ancestors(model.id!!, target.id!!, owner)
            .andExpect(status().isConflict)
    }

    @Test
    fun `hides absent and foreign target nodes and rejects malformed ids`() {
        val foreignModel = saveModel(owner)
        val foreignNode = saveNode("foreign", targetModel = foreignModel)

        ancestors(model.id!!, UUID.randomUUID(), owner)
            .andExpect(status().isNotFound)
        ancestors(model.id!!, foreignNode.id!!, owner)
            .andExpect(status().isNotFound)
        mockMvc.perform(
            get("/api/v1/models/${model.id}/nodes/not-a-uuid/ancestors")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects a parent path crossing into another model`() {
        val foreignModel = saveModel(owner)
        val foreignParent = saveNode("foreign-parent", targetModel = foreignModel)
        val target = saveNode("target", parent = foreignParent)

        ancestors(model.id!!, target.id!!, owner)
            .andExpect(status().isConflict)
    }

    @Test
    fun `checks model ACL before traversing corrupt data`() {
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
        val root = saveNode("root")
        val target = saveNode("target", parent = root)

        listOf(owner to Role.USER, viewer to Role.USER, admin to Role.ADMIN).forEach { (user, role) ->
            ancestors(model.id!!, target.id!!, user, role)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(root.id.toString()))
        }

        val foreignModel = saveModel(owner)
        val foreignParent = saveNode("foreign-parent", targetModel = foreignModel)
        val corruptTarget = saveNode("corrupt-target", parent = foreignParent)
        ancestors(model.id!!, corruptTarget.id!!, stranger)
            .andExpect(status().isForbidden)
    }

    private fun ancestors(
        modelId: UUID,
        nodeId: UUID,
        user: Users,
        role: Role = user.role
    ) = mockMvc.perform(
        get("/api/v1/models/$modelId/nodes/$nodeId/ancestors")
            .withAuth(user.id!!, role)
    )

    private fun saveUser(role: Role): Users = usersRepository.save(
        Users(
            email = "navigation-${UUID.randomUUID()}@test.com",
            role = role,
            createdAt = Instant.now()
        )
    )

    private fun saveModel(modelOwner: Users): Models = modelsRepository.save(
        Models(
            name = "navigation-model-${UUID.randomUUID()}",
            version = "1.0.0",
            owner = modelOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNode(
        name: String,
        parent: Nodes? = null,
        attrs: String? = null,
        targetModel: Models = model
    ): Nodes = nodesRepository.save(
        Nodes(
            stableId = UUID.randomUUID(),
            name = name,
            model = targetModel,
            owner = targetModel.owner,
            nodeType = nodeType,
            parentNode = parent,
            attrs = attrs,
            createdAt = Instant.now()
        )
    )

    private fun configureRoot(root: Nodes) {
        model.attrs = """{"treeRootNodeId":"${root.id}"}"""
        model = modelsRepository.save(model)
    }

    private fun insertChain(ancestorCount: Int, targetModel: Models = model): List<UUID> {
        val ids = List(ancestorCount + 1) { UUID.randomUUID() }
        withoutCycleTrigger {
            ids.forEachIndexed { index, id ->
                jdbcTemplate.update(
                    """
                    INSERT INTO nodes
                        (id, stable_id, name, created_at, parent_node, model, owner, node_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    id,
                    UUID.randomUUID(),
                    "chain-$index",
                    Timestamp.from(Instant.now()),
                    ids.getOrNull(index - 1),
                    targetModel.id,
                    targetModel.owner.id,
                    nodeType.id
                )
            }
        }
        return ids
    }

    private fun withoutCycleTrigger(action: () -> Unit) {
        jdbcTemplate.execute("ALTER TABLE nodes DISABLE TRIGGER nodes_circular_reference_check")
        try {
            action()
        } finally {
            jdbcTemplate.execute("ALTER TABLE nodes ENABLE TRIGGER nodes_circular_reference_check")
        }
    }

    private companion object {
        const val HIDDEN_ROOT_ATTRS = """{"system":{"hiddenTreeRoot":true}}"""
    }
}
