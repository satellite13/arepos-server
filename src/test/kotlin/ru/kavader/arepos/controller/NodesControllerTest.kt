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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
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
                    stableId = java.util.UUID.randomUUID(),
                    name = "Node-A",
                    model = model,
                    owner = owner,
                    nodeType = nodeType,
                    createdAt = Instant.now()
                ),
                ru.kavader.arepos.model.Nodes(
                    stableId = java.util.UUID.randomUUID(),
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
    fun `preserves existing parent when parentNodeId is omitted from update`() {
        val folder = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = java.util.UUID.randomUUID(),
                name = "Folder",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )
        val child = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = java.util.UUID.randomUUID(),
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
                stableId = java.util.UUID.randomUUID(),
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
                stableId = java.util.UUID.randomUUID(),
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
}
