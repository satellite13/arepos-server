package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class UsersControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Test
    fun `creates user via REST`() {
        val payload = UserRequest(
            email = "test@example.com",
            attrs = """{"role":"admin"}"""
        )

        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.attrs").value("""{"role":"admin"}"""))

        assertEquals(1, usersRepository.count())
    }

    @Test
    fun `lists users with pagination`() {
        usersRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Users(
                    email = "user1@test.com",
                    createdAt = Instant.now()
                ),
                ru.kavader.arepos.model.Users(
                    email = "user2@test.com",
                    createdAt = Instant.now()
                )
            )
        )

        mockMvc.perform(get("/api/v1/users?page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `filters users by email`() {
        usersRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Users(
                    email = "john@test.com",
                    createdAt = Instant.now()
                ),
                ru.kavader.arepos.model.Users(
                    email = "jane@test.com",
                    createdAt = Instant.now()
                )
            )
        )

        mockMvc.perform(get("/api/v1/users?email=john&page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].email").value("john@test.com"))
    }

    @Test
    fun `updates user`() {
        val user = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "old@test.com",
                createdAt = Instant.now()
            )
        )

        val payload = UserUpdateRequest(
            email = "new@test.com",
            attrs = """{"updated":true}"""
        )

        mockMvc.perform(
            put("/api/v1/users/${user.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("new@test.com"))
            .andExpect(jsonPath("$.attrs").value("""{"updated":true}"""))
    }

    @Test
    fun `deletes user`() {
        val user = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "delete@test.com",
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(delete("/api/v1/users/${user.id}"))
            .andExpect(status().isNoContent)

        assertEquals(0, usersRepository.count())
    }
}

