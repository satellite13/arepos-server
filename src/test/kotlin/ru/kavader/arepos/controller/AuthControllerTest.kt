package ru.kavader.arepos.controller
import ru.kavader.arepos.dto.auth.*

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
import ru.kavader.arepos.repository.UsersRepository

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    private fun registerRequest(email: String): RegisterRequest = RegisterRequest(
        email = email,
        password = "password123",
        firstName = "Иван",
        lastName = "Иванов",
        middleName = "Иванович",
        position = "Архитектор"
    )

    @Test
    fun `registers new user and returns tokens`() {
        val payload = registerRequest("newuser@test.com")

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("newuser@test.com"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.user.firstName").value("Иван"))
            .andExpect(jsonPath("$.user.lastName").value("Иванов"))
            .andExpect(jsonPath("$.user.position").value("Архитектор"))
    }

    @Test
    fun `returns 409 for duplicate email on register`() {
        val payload = registerRequest("dup@test.com")

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `logs in with valid credentials`() {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest("login@test.com")))
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("login@test.com", "password123")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("login@test.com"))
    }

    @Test
    fun `returns 401 for invalid credentials`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("nonexist@test.com", "wrong")))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `refreshes access token`() {
        val registerJson = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest("refresh@test.com")))
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response.contentAsString

        val authResponse = objectMapper.readValue(registerJson, AuthResponse::class.java)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest(authResponse.refreshToken)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `returns current user info via me endpoint`() {
        val registerJson = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest("me@test.com")))
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response.contentAsString

        val authResponse = objectMapper.readValue(registerJson, AuthResponse::class.java)

        mockMvc.perform(
            get("/api/v1/auth/me")
                .header("Authorization", "Bearer ${authResponse.accessToken}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("me@test.com"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.firstName").value("Иван"))
            .andExpect(jsonPath("$.lastName").value("Иванов"))
    }

    @Test
    fun `returns 401 for unauthenticated request`() {
        mockMvc.perform(get("/api/v1/models?page=0&size=10"))
            .andExpect(status().isUnauthorized)
    }
}
