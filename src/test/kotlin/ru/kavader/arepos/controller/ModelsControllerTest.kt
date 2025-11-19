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
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ModelsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Test
    fun `creates model via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner@test.com",
                createdAt = Instant.now()
            )
        )

        val payload = ModelRequest(
            name = "rest-model",
            version = "1.0.0",
            ownerId = owner.id!!,
            attrs = """{"key":"value"}"""
        )

        mockMvc.perform(
            post("/api/v1/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("rest-model"))
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))

        assertEquals(1, modelsRepository.count())
    }

    @Test
    fun `lists models`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-list@test.com",
                createdAt = Instant.now()
            )
        )
        modelsRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Models(
                    name = "model-1",
                    createdAt = Instant.now(),
                    version = "1.0.0",
                    owner = owner
                ),
                ru.kavader.arepos.model.Models(
                    name = "model-2",
                    createdAt = Instant.now(),
                    version = "1.0.1",
                    owner = owner
                )
            )
        )

        mockMvc.perform(get("/api/v1/models?page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }
}

