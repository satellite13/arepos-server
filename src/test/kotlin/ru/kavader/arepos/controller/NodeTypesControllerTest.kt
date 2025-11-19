package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.support.PostgresContainerTest
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class NodeTypesControllerTest : PostgresContainerTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var componentsRepository: ru.kavader.arepos.repository.ComponentsRepository

    @Autowired
    lateinit var nodesRepository: ru.kavader.arepos.repository.NodesRepository

    @BeforeEach
    fun cleanDatabase() {
        componentsRepository.deleteAll()
        nodesRepository.deleteAll()
        nodeTypesRepository.deleteAll()
        usersRepository.deleteAll()
    }

    @Test
    fun `creates node type via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-node-type@test.com",
                createdAt = Instant.now()
            )
        )

        val payload = NodeTypeRequest(
            name = "test-node-type-${System.currentTimeMillis()}",
            ownerId = owner.id!!,
            attrs = """{"key":"value"}"""
        )

        mockMvc.perform(
            post("/api/v1/node-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("test-node-type"))
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))

        assertEquals(1, nodeTypesRepository.count())
    }

    @Test
    fun `lists node types`() {
        val timestamp = System.currentTimeMillis()
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-list-node-type-$timestamp@test.com",
                createdAt = Instant.now()
            )
        )
        nodeTypesRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.NodeTypes(
                    name = "node-type-1-$timestamp",
                    createdAt = Instant.now(),
                    owner = owner
                ),
                ru.kavader.arepos.model.NodeTypes(
                    name = "node-type-2-$timestamp",
                    createdAt = Instant.now(),
                    owner = owner
                )
            )
        )

        mockMvc.perform(get("/api/v1/node-types?page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }
}

