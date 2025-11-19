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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ComponentsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var componentsRepository: ComponentsRepository

    private lateinit var owner: ru.kavader.arepos.model.Users
    private lateinit var notation: ru.kavader.arepos.model.Notations
    private lateinit var nodeType: ru.kavader.arepos.model.NodeTypes

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "component-owner-${UUID.randomUUID()}@test.com",
                createdAt = Instant.now()
            )
        )
        notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "notation-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        nodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "component-node-type-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = owner
            )
        )
    }

    @Test
    fun `creates component via REST`() {
        val payload = ComponentRequest(
            name = "Component-${System.currentTimeMillis()}",
            version = "1.0.0",
            notationId = notation.id!!,
            ownerId = owner.id!!,
            nodeTypeId = nodeType.id!!,
            attrs = """{"tier":"service"}"""
        )

        mockMvc.perform(
            post("/api/v1/components")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value(payload.name))
            .andExpect(jsonPath("$.notationId").value(notation.id.toString()))

        assertEquals(1, componentsRepository.count())
    }

    @Test
    fun `lists components`() {
        componentsRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Components(
                    name = "Component-A",
                    createdAt = Instant.now(),
                    version = "1.0.0",
                    notation = notation,
                    owner = owner,
                    nodeType = nodeType
                ),
                ru.kavader.arepos.model.Components(
                    name = "Component-B",
                    createdAt = Instant.now(),
                    version = "1.0.0",
                    notation = notation,
                    owner = owner,
                    nodeType = nodeType
                )
            )
        )

        mockMvc.perform(get("/api/v1/components?page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
    }
}


