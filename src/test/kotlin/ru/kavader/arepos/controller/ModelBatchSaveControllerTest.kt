package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ModelBatchSaveControllerTest : ControllerIntegrationTest() {

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

    @Test
    fun `batch save returns 409 when baseUpdatedAt mismatches node`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m1",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt1",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val baseTime = Instant.parse("2024-06-01T10:00:00Z")
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "node1",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = baseTime
            )
        )
        val nodeId = node.id!!

        val staleBase = Instant.parse("2024-06-01T09:00:00Z")
        val payload = mapOf(
            "nodes" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to nodeId.toString(),
                        "name" to "renamed",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to null,
                        "baseUpdatedAt" to staleBase.toString()
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Concurrent modification"))
            .andExpect(jsonPath("$.conflicts[0].kind").value("node"))
            .andExpect(jsonPath("$.conflicts[0].id").value(nodeId.toString()))

        val reloaded = nodesRepository.findById(nodeId).orElseThrow()
        assertEquals("node1", reloaded.name)
    }

    @Test
    fun `batch save succeeds when baseUpdatedAt matches`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner2@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m2",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt2",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val baseTime = Instant.parse("2024-07-01T12:00:00Z")
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "n",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = baseTime
            )
        )
        val nodeId = node.id!!

        val payload = mapOf(
            "nodes" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to nodeId.toString(),
                        "name" to "ok-name",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to null,
                        "baseUpdatedAt" to baseTime.toString()
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)

        assertEquals("ok-name", nodesRepository.findById(nodeId).orElseThrow().name)
    }

    @Test
    fun `batch save skips version check when force is true`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner3@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m3",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt3",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val baseTime = Instant.parse("2024-08-01T08:00:00Z")
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "force-node",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = baseTime
            )
        )
        val nodeId = node.id!!

        val payload = mapOf(
            "force" to true,
            "nodes" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to nodeId.toString(),
                        "name" to "forced",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to null,
                        "baseUpdatedAt" to "2000-01-01T00:00:00Z"
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)

        assertEquals("forced", nodesRepository.findById(nodeId).orElseThrow().name)
    }

    @Test
    fun `batch save accepts legacy delete as uuid strings`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner4@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m4",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt4",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "to-delete",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val nodeId = node.id!!

        val payload = mapOf(
            "nodes" to mapOf(
                "delete" to listOf(nodeId.toString())
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)

        assertEquals(false, nodesRepository.existsById(nodeId))
    }
}
