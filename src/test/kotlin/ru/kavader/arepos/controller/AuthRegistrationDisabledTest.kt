package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.auth.AdminRegisterRequest
import ru.kavader.arepos.dto.auth.RegisterRequest

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "arepos.auth.registration-enabled=false",
        "arepos.admin-secret=test-admin-secret"
    ]
)
class AuthRegistrationDisabledTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `returns 403 when registration is disabled`() {
        val payload = RegisterRequest(
            email = "blocked@test.com",
            password = "Password1",
            firstName = "Test",
            lastName = "User"
        )

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `returns 403 for register-admin when registration is disabled`() {
        val payload = AdminRegisterRequest(
            email = "admin-blocked@test.com",
            password = "Password1",
            firstName = "Admin",
            lastName = "Blocked",
            adminSecret = "test-admin-secret"
        )

        mockMvc.perform(
            post("/api/v1/auth/register-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isForbidden)
    }
}
