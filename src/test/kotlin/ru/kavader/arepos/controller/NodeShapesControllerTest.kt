package ru.kavader.arepos.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class NodeShapesControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var nodeShapesRepository: NodeShapesRepository

    private lateinit var owner: Users
    private lateinit var outsider: Users
    private lateinit var ownerShape: NodeShapes
    private lateinit var outsiderShape: NodeShapes

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            Users(
                email = "node-shape-owner-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        outsider = usersRepository.save(
            Users(
                email = "node-shape-outsider-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        ownerShape = nodeShapesRepository.save(
            NodeShapes(
                name = "owner-shape-${UUID.randomUUID()}",
                owner = owner,
                outline = """[{"type":"M","x":0,"y":0}]""",
                createdAt = Instant.now()
            )
        )
        outsiderShape = nodeShapesRepository.save(
            NodeShapes(
                name = "outsider-shape-${UUID.randomUUID()}",
                owner = outsider,
                outline = """[{"type":"M","x":1,"y":1}]""",
                createdAt = Instant.now()
            )
        )
    }

    @Test
    fun `denies get for non-owner without share`() {
        mockMvc.perform(
            get("/api/v1/node-shapes/${ownerShape.id}")
                .withAuth(outsider.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `allows get for owner`() {
        mockMvc.perform(
            get("/api/v1/node-shapes/${ownerShape.id}")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(ownerShape.id.toString()))
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))
    }

    @Test
    fun `list returns only visible node shapes`() {
        mockMvc.perform(
            get("/api/v1/node-shapes?page=0&size=10")
                .withAuth(outsider.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(outsiderShape.id.toString()))
    }
}
