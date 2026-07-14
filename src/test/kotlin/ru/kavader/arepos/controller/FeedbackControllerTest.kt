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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.site.CreateFeedbackCommentRequest
import ru.kavader.arepos.dto.site.CreateFeedbackRequest
import ru.kavader.arepos.dto.site.UpdateFeedbackRequest
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class FeedbackControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Test
    fun `anonymous can list feedback`() {
        mockMvc.perform(get("/api/v1/feedback"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
    }

    @Test
    fun `anonymous cannot create feedback`() {
        mockMvc.perform(
            post("/api/v1/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateFeedbackRequest("idea", "Title", "Body")))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `authenticated user can create vote comment and unvote`() {
        val user = persistUser("feedback-user@test.com")
        val createBody = objectMapper.writeValueAsString(
            CreateFeedbackRequest(type = "idea", title = "SVG export", body = "Need better SVG export")
        )

        val created = mockMvc.perform(
            post("/api/v1/feedback")
                .withAuth(user.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("SVG export"))
            .andExpect(jsonPath("$.voteCount").value(0))
            .andReturn()

        val id = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(post("/api/v1/feedback/$id/votes").withAuth(user.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.voteCount").value(1))
            .andExpect(jsonPath("$.votedByMe").value(true))

        mockMvc.perform(post("/api/v1/feedback/$id/votes").withAuth(user.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.voteCount").value(1))

        mockMvc.perform(
            post("/api/v1/feedback/$id/comments")
                .withAuth(user.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateFeedbackCommentRequest("Looks good")))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.body").value("Looks good"))

        mockMvc.perform(get("/api/v1/feedback/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comments.length()").value(1))

        mockMvc.perform(delete("/api/v1/feedback/$id/votes").withAuth(user.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.voteCount").value(0))
    }

    @Test
    fun `admin can change status non-admin cannot`() {
        val author = persistUser("feedback-author@test.com")
        val admin = persistUser("feedback-admin@test.com", Role.ADMIN)
        val other = persistUser("feedback-other@test.com")

        val created = mockMvc.perform(
            post("/api/v1/feedback")
                .withAuth(author.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateFeedbackRequest("bug", "Zoom bug", "Trackpad zoom broken")
                    )
                )
        )
            .andExpect(status().isCreated)
            .andReturn()
        val id = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(
            patch("/api/v1/feedback/$id")
                .withAuth(other.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateFeedbackRequest(status = "planned")))
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            patch("/api/v1/feedback/$id")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateFeedbackRequest(status = "planned")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("planned"))
    }

    private fun persistUser(email: String, role: Role = Role.USER): Users =
        usersRepository.save(
            Users(
                email = email,
                role = role,
                createdAt = Instant.now()
            )
        )
}
