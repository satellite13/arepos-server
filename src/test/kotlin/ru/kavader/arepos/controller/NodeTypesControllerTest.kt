package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class NodeTypesControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Test
    fun `creates node type via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-node-type@test.com",
                role = Role.EDITOR,
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
                .withAuth(owner.id!!, Role.EDITOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value(payload.name))
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))

        assertEquals(1, nodeTypesRepository.count())
    }

    @Test
    fun `lists node types`() {
        val timestamp = System.currentTimeMillis()
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-list-node-type-$timestamp@test.com",
                role = Role.ADMIN,
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

        mockMvc.perform(
            get("/api/v1/node-types?page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(2))
    }

    @Test
    fun `user sees only own node types`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-type-a@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-type-b@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val ownType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "own-node-type",
                createdAt = Instant.now(),
                owner = userA
            )
        )
        nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "foreign-node-type",
                createdAt = Instant.now(),
                owner = userB
            )
        )

        mockMvc.perform(
            get("/api/v1/node-types?page=0&size=10")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(ownType.id.toString()))
    }

    @Test
    fun `user can create own node type`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-create-node-type@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val payload = NodeTypeRequest(
            name = "user-node-type-${System.currentTimeMillis()}",
            ownerId = owner.id!!,
            attrs = """{"scope":"own"}"""
        )

        mockMvc.perform(
            post("/api/v1/node-types")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))
            .andExpect(jsonPath("$.name").value(payload.name))
    }

    @Test
    fun `ignores foreign ownerId for non-admin, creates under own identity`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-a-node-type-create@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-b-node-type-create@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val payload = NodeTypeRequest(
            name = "owned-node-type-${System.currentTimeMillis()}",
            ownerId = userB.id!!,
            attrs = null
        )

        mockMvc.perform(
            post("/api/v1/node-types")
                .withAuth(userA.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerId").value(userA.id.toString()))
    }

    @Test
    fun `directory node type is readable for any user`() {
        val systemUser = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "system-public@test.com",
                role = Role.USER,
                isActive = false,
                createdAt = Instant.now()
            )
        )
        val directoryType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "Directory",
                attrs = """{"system":true,"kind":"directory"}""",
                createdAt = Instant.now(),
                owner = systemUser
            )
        )
        val regularUser = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "regular-reader@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/node-types/${directoryType.id}")
                .withAuth(regularUser.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Directory"))
    }
}
