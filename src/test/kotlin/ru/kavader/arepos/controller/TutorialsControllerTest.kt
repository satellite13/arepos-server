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
import ru.kavader.arepos.dto.site.CreateTutorialRequest
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class TutorialsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Test
    fun `anonymous lists published tutorials`() {
        mockMvc.perform(get("/api/v1/tutorials"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `admin creates tutorial with youtube embed`() {
        val admin = persistUser("tutorial-admin@test.com", Role.ADMIN)
        mockMvc.perform(
            post("/api/v1/tutorials")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateTutorialRequest(
                            title = "Getting started",
                            description = "Intro",
                            provider = "youtube",
                            externalId = "dQw4w9WgXcQ"
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.embedUrl").value("https://www.youtube.com/embed/dQw4w9WgXcQ"))

        mockMvc.perform(get("/api/v1/tutorials"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `admin cannot use disallowed embed host`() {
        val admin = persistUser("tutorial-bad-host@test.com", Role.ADMIN)
        mockMvc.perform(
            post("/api/v1/tutorials")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateTutorialRequest(
                            title = "Evil",
                            provider = "youtube",
                            externalId = "x",
                            embedUrl = "https://evil.example/embed/x"
                        )
                    )
                )
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `user cannot create tutorial`() {
        val user = persistUser("tutorial-user@test.com")
        mockMvc.perform(
            post("/api/v1/tutorials")
                .withAuth(user.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateTutorialRequest(
                            title = "Nope",
                            provider = "youtube",
                            externalId = "abc"
                        )
                    )
                )
        ).andExpect(status().isForbidden)
    }

    private fun persistUser(email: String, role: Role = Role.USER): Users =
        usersRepository.save(Users(email = email, role = role, createdAt = Instant.now()))
}
