package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.apikey.ApiKeyGrantDto
import ru.kavader.arepos.dto.apikey.ApiKeyModes
import ru.kavader.arepos.dto.apikey.ApiKeyScopes
import ru.kavader.arepos.dto.model.NodeRequest
import ru.kavader.arepos.dto.model.NodeUpdateRequest
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class NodesControllerTest : ControllerIntegrationTest() {

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

    private lateinit var owner: ru.kavader.arepos.model.Users
    private lateinit var model: ru.kavader.arepos.model.Models
    private lateinit var nodeType: ru.kavader.arepos.model.NodeTypes

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-owner-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        nodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "node-type-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = owner
            )
        )
    }

    @Test
    fun `creates node via REST`() {
        val payload = NodeRequest(
            name = "Node-${System.currentTimeMillis()}",
            modelId = model.id!!,
            ownerId = owner.id!!,
            nodeTypeId = nodeType.id!!,
            attrs = """{"priority":1}"""
        )

        mockMvc.perform(
            post("/api/v1/nodes")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value(payload.name))
            .andExpect(jsonPath("$.modelId").value(model.id.toString()))
            .andExpect(jsonPath("$.nodeTypeId").value(nodeType.id.toString()))

        assertEquals(1, nodesRepository.count())
    }

    @Test
    fun `lists nodes`() {
        nodesRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Nodes(
                    stableId = UUID.randomUUID(),
                    name = "Node-A",
                    model = model,
                    owner = owner,
                    nodeType = nodeType,
                    createdAt = Instant.now()
                ),
                ru.kavader.arepos.model.Nodes(
                    stableId = UUID.randomUUID(),
                    name = "Node-B",
                    model = model,
                    owner = owner,
                    nodeType = nodeType,
                    createdAt = Instant.now()
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/nodes?page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(2))
    }

    @Test
    fun `lists configured and legacy root children with lazy tree semantics`() {
        val directoryType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "Directory",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val hiddenRoot = saveNode("__model_tree_root__", directoryType, attrs = """{"system":{"hiddenTreeRoot":true}}""")
        val first = saveNode("First", directoryType, hiddenRoot, """{"treeOrder":1}""")
        saveNode("Second", nodeType, hiddenRoot, """{"treeOrder":2}""")
        saveNode("Grandchild", nodeType, first)
        model.attrs = """{"treeRootNodeId":"${hiddenRoot.id}"}"""
        modelsRepository.save(model)

        mockMvc.perform(
            get("/api/v1/nodes?modelId=${model.id}&parentId=root&page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value(first.id.toString()))
            .andExpect(jsonPath("$.content[0].stableId").value(first.stableId.toString()))
            .andExpect(jsonPath("$.content[0].name").value(first.name))
            .andExpect(jsonPath("$.content[0].modelId").value(model.id.toString()))
            .andExpect(jsonPath("$.content[0].parentNodeId").value(hiddenRoot.id.toString()))
            .andExpect(jsonPath("$.content[0].attrs").value(containsString("\"treeOrder\"")))
            .andExpect(jsonPath("$.content[0].hasChildren").value(true))

        val legacyModel = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "legacy-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner,
                attrs = "{}"
            )
        )
        val legacyRoot = saveNode("Legacy root", nodeType, targetModel = legacyModel)

        mockMvc.perform(
            get("/api/v1/nodes?modelId=${legacyModel.id}&parentId=root")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(legacyRoot.id.toString()))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"treeRootNodeId":null}""",
            """{"treeRootNodeId":""}""",
            """{"treeRootNodeId":{}}""",
            """{"treeRootNodeId":[]}""",
            """{"treeRootNodeId":"not-a-uuid"}"""
        ]
    )
    fun `rejects present invalid tree root configuration`(attrs: String) {
        model.attrs = attrs
        modelsRepository.save(model)

        mockMvc.perform(
            get("/api/v1/nodes?modelId=${model.id}&parentId=root")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `validates lazy tree parent scope and preserves unscoped list`() {
        val foreignModel = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "foreign-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val foreignParent = saveNode("Foreign parent", nodeType, targetModel = foreignModel)

        mockMvc.perform(get("/api/v1/nodes?parentId=root").withAuth(owner.id!!))
            .andExpect(status().isBadRequest)
        mockMvc.perform(
            get("/api/v1/nodes?modelId=${model.id}&parentId=${foreignParent.id}")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isNotFound)

        model.attrs = """{"treeRootNodeId":"${UUID.randomUUID()}"}"""
        modelsRepository.save(model)
        mockMvc.perform(
            get("/api/v1/nodes?modelId=${model.id}&parentId=root")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isConflict)

        mockMvc.perform(get("/api/v1/nodes?page=0&size=10").withAuth(owner.id!!))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(1))
    }

    @Test
    fun `lazy tree filters folders and system nodes and caps pages`() {
        val directoryType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "dIrEcToRy",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val parent = saveNode("Parent", directoryType)
        val folder = saveNode("Folder", directoryType, parent)
        saveNode("Regular", nodeType, parent)
        saveNode("Hidden", directoryType, parent, """{"system":{"hiddenTreeRoot":true}}""")
        saveNode("Folder child", directoryType, folder)

        mockMvc.perform(
            get(
                "/api/v1/nodes?modelId=${model.id}&parentId=${parent.id}" +
                    "&foldersOnly=true&excludeSystem=true&size=1000"
            ).withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(500))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(folder.id.toString()))
            .andExpect(jsonPath("$.content[0].hasChildren").value(true))
    }

    @Test
    fun `lazy tree applies model ACL before pagination for all allowed callers`() {
        val viewer = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "viewer-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val stranger = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "stranger-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
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
        val parent = saveNode("Parent", nodeType)
        val first = saveNode("First", nodeType, parent, """{"treeOrder":1}""")
        val second = saveNode("Second", nodeType, parent, """{"treeOrder":2}""")
        val expectedIds = listOf(first.id.toString(), second.id.toString())
        val ownerIds = lazyTreeIds(owner.id!!, Role.USER, model.id!!, parent.id!!)
        val sharedIds = lazyTreeIds(viewer.id!!, Role.USER, model.id!!, parent.id!!)
        val adminIds = lazyTreeIds(admin.id!!, Role.ADMIN, model.id!!, parent.id!!)
        val mcpToken = jwtTokenProvider.generateMcpAccessToken(
            owner.id!!,
            Role.USER.name,
            ApiKeyModes.GRANTS,
            null,
            listOf(ApiKeyGrantDto(model.id!!, listOf(ApiKeyScopes.MODELS_READ)))
        )
        val mcpResult = mockMvc.perform(
            get("/api/v1/nodes?modelId=${model.id}&parentId=${parent.id}&size=1")
                .header("Authorization", "Bearer $mcpToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andReturn()
        val mcpIds = objectMapper.readTree(mcpResult.response.contentAsString)
            .path("content")
            .map { it.path("id").asText() }

        assertEquals(expectedIds, ownerIds)
        assertEquals(ownerIds, sharedIds)
        assertEquals(ownerIds, adminIds)
        assertEquals(expectedIds.take(1), mcpIds)

        mockMvc.perform(
            get("/api/v1/nodes?modelId=${model.id}&parentId=${parent.id}")
                .withAuth(stranger.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `honors requested page size up to configured max of 25000`() {
        mockMvc.perform(
            get("/api/v1/nodes?page=0&size=25000")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(25000))

        mockMvc.perform(
            get("/api/v1/nodes?page=0&size=30000")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(25000))
    }

    @Test
    fun `uses configured default page size when size is omitted`() {
        mockMvc.perform(
            get("/api/v1/nodes")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(50))
    }

    @Test
    fun `preserves existing parent when parentNodeId is omitted from update`() {
        val folder = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = UUID.randomUUID(),
                name = "Folder",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )
        val child = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = UUID.randomUUID(),
                name = "Child",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = folder,
                createdAt = Instant.now()
            )
        )

        // parentNodeId = null means "don't change parent", not "clear parent"
        val payload = NodeUpdateRequest(
            name = "Renamed-Child",
            modelId = model.id!!,
            ownerId = owner.id!!,
            nodeTypeId = nodeType.id!!,
            parentNodeId = null,
            attrs = child.attrs
        )

        mockMvc.perform(
            put("/api/v1/nodes/${child.id}")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Renamed-Child"))
            .andExpect(jsonPath("$.parentNodeId").value(folder.id.toString()))

        val reloaded = nodesRepository.findById(child.id!!).orElseThrow()
        assertEquals(folder.id, reloaded.parentNode?.id)
    }

    @Test
    fun `forbids update of system tree root node`() {
        val rootNode = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = UUID.randomUUID(),
                name = "__model_tree_root__",
                model = model,
                owner = owner,
                nodeType = nodeType,
                attrs = """{"system":{"hiddenTreeRoot":true}}""",
                createdAt = Instant.now()
            )
        )

        val payload = NodeUpdateRequest(
            name = "new-name",
            modelId = model.id!!,
            ownerId = owner.id!!,
            nodeTypeId = nodeType.id!!,
            parentNodeId = null
        )

        mockMvc.perform(
            put("/api/v1/nodes/${rootNode.id}")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `forbids delete of system tree root node`() {
        val rootNode = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = UUID.randomUUID(),
                name = "__model_tree_root__",
                model = model,
                owner = owner,
                nodeType = nodeType,
                attrs = """{"system":{"hiddenTreeRoot":true}}""",
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            delete("/api/v1/nodes/${rootNode.id}")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `non-admin create node ignores provided ownerId and uses current user`() {
        val actor = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-user-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val foreignOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-foreign-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val actorModel = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-user-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = actor
            )
        )
        val actorNodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "node-type-user-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = actor
            )
        )
        val payload = NodeRequest(
            name = "Node-User",
            modelId = actorModel.id!!,
            ownerId = foreignOwner.id!!,
            nodeTypeId = actorNodeType.id!!,
            attrs = """{"x":1}"""
        )

        val mvcResult = mockMvc.perform(
            post("/api/v1/nodes")
                .withAuth(actor.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerId").value(actor.id.toString()))
            .andReturn()

        val createdId = UUID.fromString(objectMapper.readTree(mvcResult.response.contentAsString).path("id").asText())
        val createdNode = nodesRepository.findById(createdId).orElseThrow()
        assertEquals(actor.id, createdNode.owner.id)
    }

    @Test
    fun `admin update node can reassign owner`() {
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-admin-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val newOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-new-owner-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val adminModel = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-admin-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = admin
            )
        )
        val adminNodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "node-type-admin-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = admin
            )
        )
        val node = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = UUID.randomUUID(),
                name = "Owned by admin",
                model = adminModel,
                owner = admin,
                nodeType = adminNodeType,
                createdAt = Instant.now()
            )
        )
        val payload = NodeUpdateRequest(
            name = "Reassigned node",
            modelId = adminModel.id!!,
            ownerId = newOwner.id!!,
            nodeTypeId = adminNodeType.id!!,
            parentNodeId = null,
            attrs = node.attrs
        )

        mockMvc.perform(
            put("/api/v1/nodes/${node.id}")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ownerId").value(newOwner.id.toString()))

        val reloaded = nodesRepository.findById(node.id!!).orElseThrow()
        assertEquals(newOwner.id, reloaded.owner.id)
    }

    private fun saveNode(
        name: String,
        type: ru.kavader.arepos.model.NodeTypes,
        parent: ru.kavader.arepos.model.Nodes? = null,
        attrs: String? = null,
        targetModel: ru.kavader.arepos.model.Models = model
    ): ru.kavader.arepos.model.Nodes = nodesRepository.save(
        ru.kavader.arepos.model.Nodes(
            stableId = UUID.randomUUID(),
            name = name,
            model = targetModel,
            owner = targetModel.owner,
            nodeType = type,
            parentNode = parent,
            attrs = attrs,
            createdAt = Instant.now()
        )
    )

    private fun lazyTreeIds(
        userId: UUID,
        role: Role,
        modelId: UUID,
        parentId: UUID
    ): List<String> {
        val result = mockMvc.perform(
            get("/api/v1/nodes?modelId=$modelId&parentId=$parentId&size=10")
                .withAuth(userId, role)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString)
            .path("content")
            .map { it.path("id").asText() }
    }
}
