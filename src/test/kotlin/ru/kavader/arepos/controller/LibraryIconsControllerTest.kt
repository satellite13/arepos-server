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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class LibraryIconsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    private val sampleSvg =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path d="M1 1h8v8H1z"/></svg>"""

    @Test
    fun `user can list and cannot create`() {
        val user = persist("lib-icons-user@test.com", Role.USER)
        mockMvc.perform(get("/api/v1/library-icons").withAuth(user.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)

        mockMvc.perform(
            post("/api/v1/library-icons")
                .withAuth(user.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "acme-app", "svg" to sampleSvg)))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `admin creates lists exports and overwrites via bundle`() {
        val admin = persist("lib-icons-admin@test.com", Role.ADMIN)
        mockMvc.perform(
            post("/api/v1/library-icons")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "Acme App", "svg" to sampleSvg)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("acme-app"))

        mockMvc.perform(get("/api/v1/library-icons").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("acme-app"))

        mockMvc.perform(get("/api/v1/library-icons/bundle").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.format").value("warchi-icon-bundle"))
            .andExpect(jsonPath("$.icons[0].name").value("acme-app"))

        val otherSvg =
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8"><circle cx="4" cy="4" r="3"/></svg>"""
        mockMvc.perform(
            post("/api/v1/library-icons/bundle")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "format" to "warchi-icon-bundle",
                            "version" to 1,
                            "icons" to listOf(mapOf("name" to "acme-app", "svg" to otherSvg))
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.created").value(0))
            .andExpect(jsonPath("$.overwritten").value(1))
    }

    @Test
    fun `admin can delete icon`() {
        val admin = persist("lib-icons-delete@test.com", Role.ADMIN)
        val body = mockMvc.perform(
            post("/api/v1/library-icons")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "to-delete", "svg" to sampleSvg)))
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val id = objectMapper.readTree(body).path("id").asText()

        mockMvc.perform(delete("/api/v1/library-icons/$id").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isNoContent)
    }

    private fun persist(email: String, role: Role): Users =
        usersRepository.save(
            Users(
                email = email,
                role = role,
                createdAt = Instant.now()
            )
        )
}
