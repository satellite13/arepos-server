package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.auth.LoginRequest
import ru.kavader.arepos.dto.auth.RegisterRequest
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.PasswordPolicyValidator

@SpringBootTest
@AutoConfigureMockMvc
class AuthLockoutTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Test
    fun `locks account after repeated failed logins`() {
        val email = "lockout@test.com"
        val register = RegisterRequest(
            email = email,
            password = "ValidPass1",
            firstName = "Lock",
            lastName = "Out"
        )
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register))
        ).andExpect(status().isCreated)

        repeat(PasswordPolicyValidator.MAX_FAILED_ATTEMPTS) {
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(LoginRequest(email, "WrongPass1")))
            ).andExpect(status().isUnauthorized)
        }

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest(email, "ValidPass1")))
        )
            .andExpect(status().isLocked)
            .andExpect(header().exists("Retry-After"))
    }
}
