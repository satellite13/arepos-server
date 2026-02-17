package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class DiagramsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Test
    fun `creates diagram via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-diagram@test.com",
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-for-diagram",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "notation-for-diagram",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        val payload = DiagramRequest(
            name = "diagram-1",
            version = "1.0.0",
            ownerId = owner.id!!,
            modelId = model.id!!,
            notationId = notation.id!!,
            attrs = """{"layout":"auto"}"""
        )

        mockMvc.perform(
            post("/api/v1/diagrams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("diagram-1"))
            .andExpect(jsonPath("$.modelId").value(model.id.toString()))
            .andExpect(jsonPath("$.notationId").value(notation.id.toString()))

        assertEquals(1, diagramsRepository.count())
    }

    @Test
    fun `performs full CRUD flow for diagram`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-crud-diagram@test.com",
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-for-crud",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "notation-for-crud",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        val createPayload = DiagramRequest(
            name = "diagram-crud",
            version = "1.0.0",
            ownerId = owner.id!!,
            modelId = model.id!!,
            notationId = notation.id!!,
            attrs = """{"layout":"manual"}"""
        )

        val createdJson = mockMvc.perform(
            post("/api/v1/diagrams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPayload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("diagram-crud"))
            .andReturn()
            .response
            .contentAsString

        val created = objectMapper.readValue(createdJson, DiagramResponse::class.java)
        val diagramId = created.id

        mockMvc.perform(get("/api/v1/diagrams/$diagramId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(diagramId.toString()))
            .andExpect(jsonPath("$.name").value("diagram-crud"))

        val updatePayload = DiagramUpdateRequest(
            name = "diagram-crud-updated",
            version = "1.1.0",
            attrs = """{"layout":"updated"}"""
        )
        mockMvc.perform(
            put("/api/v1/diagrams/$diagramId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("diagram-crud-updated"))
            .andExpect(jsonPath("$.version").value("1.1.0"))

        mockMvc.perform(delete("/api/v1/diagrams/$diagramId"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/diagrams/$diagramId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns 404 when owner does not exist`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-existing@test.com",
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-for-404",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "notation-for-404",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        val payload = DiagramRequest(
            name = "diagram-404",
            version = "1.0.0",
            ownerId = UUID.randomUUID(),
            modelId = model.id!!,
            notationId = notation.id!!,
            attrs = null
        )

        mockMvc.perform(
            post("/api/v1/diagrams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns 404 when model does not exist`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-model-missing@test.com",
                createdAt = Instant.now()
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "notation-model-missing",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        val payload = DiagramRequest(
            name = "diagram-missing-model",
            version = "1.0.0",
            ownerId = owner.id!!,
            modelId = UUID.randomUUID(),
            notationId = notation.id!!,
            attrs = null
        )

        mockMvc.perform(
            post("/api/v1/diagrams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns 404 when notation does not exist`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-notation-missing@test.com",
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-notation-missing",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )

        val payload = DiagramRequest(
            name = "diagram-missing-notation",
            version = "1.0.0",
            ownerId = owner.id!!,
            modelId = model.id!!,
            notationId = UUID.randomUUID(),
            attrs = null
        )

        mockMvc.perform(
            post("/api/v1/diagrams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns 409 for duplicate model name version`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-dup@test.com",
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-for-dup",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "notation-for-dup",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        val payload = DiagramRequest(
            name = "diagram-dup",
            version = "1.0.0",
            ownerId = owner.id!!,
            modelId = model.id!!,
            notationId = notation.id!!,
            attrs = null
        )

        mockMvc.perform(
            post("/api/v1/diagrams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/diagrams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `lists diagrams with filters`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-list-diagram@test.com",
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-for-list",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "notation-for-list",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        diagramsRepository.save(
            ru.kavader.arepos.model.Diagrams(
                name = "diagram-list-1",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                createdAt = Instant.now()
            )
        )
        diagramsRepository.save(
            ru.kavader.arepos.model.Diagrams(
                name = "diagram-list-2",
                version = "1.0.1",
                owner = owner,
                model = model,
                notation = notation,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(get("/api/v1/diagrams?modelId=${model.id}&page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }
}
