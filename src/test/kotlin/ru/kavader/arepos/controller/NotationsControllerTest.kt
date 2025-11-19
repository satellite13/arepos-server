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
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class NotationsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Test
    fun `creates notation via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner@test.com",
                createdAt = Instant.now()
            )
        )

        val payload = NotationRequest(
            name = "test-notation",
            version = "1.0.0",
            ownerId = owner.id!!,
            attrs = """{"type":"test"}"""
        )

        mockMvc.perform(
            post("/api/v1/notations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("test-notation"))
            .andExpect(jsonPath("$.version").value("1.0.0"))

        assertEquals(1, notationsRepository.count())
    }

    @Test
    fun `lists notations with filters`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner@test.com",
                createdAt = Instant.now()
            )
        )

        notationsRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Notations(
                    name = "notation-1",
                    version = "1.0.0",
                    owner = owner,
                    createdAt = Instant.now()
                ),
                ru.kavader.arepos.model.Notations(
                    name = "notation-2",
                    version = "1.0.1",
                    owner = owner,
                    createdAt = Instant.now()
                )
            )
        )

        mockMvc.perform(get("/api/v1/notations?ownerId=${owner.id}&page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }
}

