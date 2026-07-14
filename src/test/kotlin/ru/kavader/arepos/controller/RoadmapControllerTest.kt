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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.site.CreateFeedbackRequest
import ru.kavader.arepos.dto.site.CreateRoadmapMilestoneRequest
import ru.kavader.arepos.dto.site.SetRoadmapMilestoneItemsRequest
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class RoadmapControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Test
    fun `anonymous can list roadmap`() {
        mockMvc.perform(get("/api/v1/roadmap"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `non-admin cannot create milestone`() {
        val user = persistUser("roadmap-user@test.com")
        mockMvc.perform(
            post("/api/v1/roadmap/milestones")
                .withAuth(user.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateRoadmapMilestoneRequest(title = "Q3", description = "Collab")
                    )
                )
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin creates milestone and links feedback items`() {
        val admin = persistUser("roadmap-admin@test.com", Role.ADMIN)
        val author = persistUser("roadmap-author@test.com")

        val feedback = mockMvc.perform(
            post("/api/v1/feedback")
                .withAuth(author.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateFeedbackRequest("idea", "Live sync", "Realtime diagrams")
                    )
                )
        )
            .andExpect(status().isCreated)
            .andReturn()
        val feedbackId = UUID.fromString(
            objectMapper.readTree(feedback.response.contentAsString).get("id").asText()
        )

        val created = mockMvc.perform(
            post("/api/v1/roadmap/milestones")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateRoadmapMilestoneRequest(
                            title = "Collaboration",
                            description = "Live sync and sharing",
                            status = "planned",
                            sortOrder = 1,
                            targetPeriod = "2026 Q3"
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("Collaboration"))
            .andReturn()

        val milestoneId = objectMapper.readTree(created.response.contentAsString).get("id").asText()

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/$milestoneId/items")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(SetRoadmapMilestoneItemsRequest(listOf(feedbackId))))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Live sync"))

        mockMvc.perform(get("/api/v1/roadmap"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].items.length()").value(1))
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
