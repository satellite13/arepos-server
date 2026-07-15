package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.site.CreateFeedbackCommentRequest
import ru.kavader.arepos.dto.site.CreateFeedbackRequest
import ru.kavader.arepos.dto.site.CreateRoadmapMilestoneRequest
import ru.kavader.arepos.dto.site.MergeFeedbackRequest
import ru.kavader.arepos.dto.site.SetRoadmapMilestoneItemsRequest
import ru.kavader.arepos.dto.site.UpdateFeedbackRequest
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    fun `public feedback list searches title and body with filters and pagination`() {
        val admin = persistUser("feedback-search-admin@test.com", Role.ADMIN)

        fun create(type: String, title: String, body: String): String =
            objectMapper.readTree(
                mockMvc.perform(
                    post("/api/v1/feedback")
                        .withAuth(admin.id!!, Role.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateFeedbackRequest(type, title, body)))
                )
                    .andExpect(status().isCreated)
                    .andReturn()
                    .response
                    .contentAsString
            ).get("id").asText()

        val diagramExportId = create("idea", "Diagram export", "Export diagrams to SVG")
        val plannedDiagramId = create("bug", "Autosave", "DIAGRAM state is lost after refresh")
        create("idea", "Keyboard shortcuts", "Add shortcut customization")
        val voter = persistUser("feedback-search-voter@test.com")
        mockMvc.perform(post("/api/v1/feedback/$diagramExportId/votes").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/feedback/$plannedDiagramId/votes").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/feedback/$plannedDiagramId/votes").withAuth(voter.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(
            patch("/api/v1/feedback/$plannedDiagramId")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateFeedbackRequest(status = "planned")))
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/feedback?size=100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(3))
        mockMvc.perform(get("/api/v1/feedback?type=idea&size=100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
        mockMvc.perform(get("/api/v1/feedback?q=diagram&size=1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items.length()").value(1))
        mockMvc.perform(get("/api/v1/feedback?q=diagram&sort=votes&size=100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].title").value("Autosave"))
            .andExpect(jsonPath("$.items[1].title").value("Diagram export"))
        mockMvc.perform(get("/api/v1/feedback?q=diagram&sort=recent&size=100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].title").value("Autosave"))
            .andExpect(jsonPath("$.items[1].title").value("Diagram export"))
        mockMvc.perform(get("/api/v1/feedback?q=diagram&type=idea&size=100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Diagram export"))
        mockMvc.perform(get("/api/v1/feedback?q=diagram&status=planned&size=100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Autosave"))
        mockMvc.perform(
            get("/api/v1/feedback")
                .param("q", "   ")
                .param("type", "idea")
                .param("size", "100")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
    }

    @Test
    fun `public feedback search treats LIKE wildcards as literal text`() {
        val author = persistUser("feedback-search-wildcards@test.com")
        val literalId = createFeedback(author, "Search 100%_\\value token", "Literal wildcard search")
        createFeedback(author, "Search 100AA\\value token", "Wildcard-like candidate")

        mockMvc.perform(
            get("/api/v1/feedback")
                .param("q", "100%_\\value")
                .param("size", "100")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(literalId))
    }

    @Test
    fun `public feedback search paginates tied votes and recency deterministically`() {
        val author = persistUser("feedback-search-order@test.com")
        val ids = listOf(
            createFeedback(author, "Tied search first", "Tied search"),
            createFeedback(author, "Tied search second", "Tied search"),
            createFeedback(author, "Tied search third", "Tied search")
        )
        val tiedCreatedAt = java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        ids.forEach { id ->
            jdbcTemplate.update("UPDATE public.feedback_items SET created_at = ? WHERE id = ?", tiedCreatedAt, UUID.fromString(id))
        }
        val expectedIds = ids.sortedDescending()

        fun pagedIds(sort: String, page: Int): List<String> =
            objectMapper.readTree(
                mockMvc.perform(
                    get("/api/v1/feedback")
                        .param("q", "tied search")
                        .param("sort", sort)
                        .param("page", page.toString())
                        .param("size", "2")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.total").value(3))
                    .andReturn()
                    .response
                    .contentAsString
            ).get("items").map { it.get("id").asText() }

        assertEquals(expectedIds, pagedIds("votes", 0) + pagedIds("votes", 1))
        assertEquals(expectedIds, pagedIds("recent", 0) + pagedIds("recent", 1))
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

    @Test
    fun `admin can change feedback type but author cannot`() {
        val author = persistUser("feedback-type-author@test.com")
        val admin = persistUser("feedback-type-admin@test.com", Role.ADMIN)
        val id = createFeedback(author, "Type change")

        mockMvc.perform(
            patch("/api/v1/feedback/$id")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateFeedbackRequest(type = "bug")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("bug"))

        mockMvc.perform(
            patch("/api/v1/feedback/$id")
                .withAuth(author.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateFeedbackRequest(type = "idea")))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin can merge feedback and feedback detail exposes merge target`() {
        val author = persistUser("feedback-merge-author@test.com")
        val admin = persistUser("feedback-merge-admin@test.com", Role.ADMIN)

        fun createFeedback(title: String): String =
            objectMapper.readTree(
                mockMvc.perform(
                    post("/api/v1/feedback")
                        .withAuth(author.id!!, Role.USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                CreateFeedbackRequest("idea", title, "Feedback body")
                            )
                        )
                )
                    .andExpect(status().isCreated)
                    .andReturn()
                    .response
                    .contentAsString
            ).get("id").asText()

        val sourceId = createFeedback("Duplicate request")
        val targetId = createFeedback("Canonical request")

        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/merge")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        MergeFeedbackRequest(UUID.fromString(targetId))
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceId").value(sourceId))
            .andExpect(jsonPath("$.targetId").value(targetId))
            .andExpect(jsonPath("$.mergedAt").exists())
            .andExpect(jsonPath("$.target.id").value(targetId))

        mockMvc.perform(get("/api/v1/feedback/$sourceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mergedIntoId").value(targetId))
            .andExpect(jsonPath("$.mergedAt").exists())
            .andExpect(jsonPath("$.status").value("declined"))
    }

    @Test
    fun `non-admin cannot merge feedback`() {
        val author = persistUser("feedback-merge-forbidden-author@test.com")
        val otherUser = persistUser("feedback-merge-forbidden-user@test.com")
        val sourceId = createFeedback(author, "Source")
        val targetId = createFeedback(author, "Target")

        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/merge")
                .withAuth(otherUser.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MergeFeedbackRequest(UUID.fromString(targetId))))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `author can delete new feedback`() {
        val author = persistUser("feedback-delete-new@test.com")
        val id = createFeedback(author, "Delete me")

        mockMvc.perform(delete("/api/v1/feedback/$id").withAuth(author.id!!, Role.USER))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/feedback/$id"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `author cannot delete planned feedback`() {
        val author = persistUser("feedback-delete-planned-author@test.com")
        val admin = persistUser("feedback-delete-planned-admin@test.com", Role.ADMIN)
        val id = createFeedback(author, "Planned feedback")

        mockMvc.perform(
            patch("/api/v1/feedback/$id")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateFeedbackRequest(status = "planned")))
        ).andExpect(status().isOk)

        mockMvc.perform(delete("/api/v1/feedback/$id").withAuth(author.id!!, Role.USER))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `admin can delete feedback with any status`() {
        val author = persistUser("feedback-delete-any-author@test.com")
        val admin = persistUser("feedback-delete-any-admin@test.com", Role.ADMIN)
        val id = createFeedback(author, "Declined feedback")

        mockMvc.perform(
            patch("/api/v1/feedback/$id")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateFeedbackRequest(status = "declined")))
        ).andExpect(status().isOk)

        mockMvc.perform(delete("/api/v1/feedback/$id").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `admin cannot delete merge target while sources reference it`() {
        val author = persistUser("feedback-delete-target-author@test.com")
        val admin = persistUser("feedback-delete-target-admin@test.com", Role.ADMIN)
        val sourceId = createFeedback(author, "Merged source")
        val targetId = createFeedback(author, "Merge target")

        mergeFeedback(sourceId, targetId, admin).andExpect(status().isOk)

        mockMvc.perform(delete("/api/v1/feedback/$targetId").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isConflict)
    }

    @Test
    fun `merge transfers votes comments and roadmap links without duplicates`() {
        val author = persistUser("feedback-transfer-author@test.com")
        val admin = persistUser("feedback-transfer-admin@test.com", Role.ADMIN)
        val sharedVoter = persistUser("feedback-transfer-shared@test.com")
        val sourceOnlyVoter = persistUser("feedback-transfer-source@test.com")
        val sourceId = createFeedback(author, "Duplicate")
        val targetId = createFeedback(author, "Canonical")

        mockMvc.perform(post("/api/v1/feedback/$sourceId/votes").withAuth(sharedVoter.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/feedback/$sourceId/votes").withAuth(sourceOnlyVoter.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/feedback/$targetId/votes").withAuth(sharedVoter.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/comments")
                .withAuth(author.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateFeedbackCommentRequest("Source comment")))
        ).andExpect(status().isCreated)

        val milestoneId = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/roadmap/milestones")
                    .withAuth(admin.id!!, Role.ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(CreateRoadmapMilestoneRequest("Feedback roadmap")))
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        ).get("id").asText()
        mockMvc.perform(
            put("/api/v1/roadmap/milestones/$milestoneId/items")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        SetRoadmapMilestoneItemsRequest(listOf(UUID.fromString(sourceId), UUID.fromString(targetId)))
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/merge")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MergeFeedbackRequest(UUID.fromString(targetId))))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.target.voteCount").value(2))

        mockMvc.perform(get("/api/v1/feedback/$targetId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.voteCount").value(2))
            .andExpect(jsonPath("$.comments.length()").value(1))
        mockMvc.perform(get("/api/v1/roadmap/milestones/$milestoneId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(targetId))
    }

    @Test
    fun `merge rejects self merge and already merged feedback`() {
        val author = persistUser("feedback-invalid-merge-author@test.com")
        val admin = persistUser("feedback-invalid-merge-admin@test.com", Role.ADMIN)
        val sourceId = createFeedback(author, "Source")
        val targetId = createFeedback(author, "Target")

        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/merge")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MergeFeedbackRequest(UUID.fromString(sourceId))))
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/merge")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MergeFeedbackRequest(UUID.fromString(targetId))))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/merge")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MergeFeedbackRequest(UUID.fromString(targetId))))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `merge rejects target that is already merged`() {
        val author = persistUser("feedback-merged-target-author@test.com")
        val admin = persistUser("feedback-merged-target-admin@test.com", Role.ADMIN)
        val sourceId = createFeedback(author, "New source")
        val mergedTargetId = createFeedback(author, "Already merged target")
        val canonicalId = createFeedback(author, "Canonical")

        mergeFeedback(mergedTargetId, canonicalId, admin).andExpect(status().isOk)

        mergeFeedback(sourceId, mergedTargetId, admin)
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `concurrent merges keep target vote count accurate`() {
        val author = persistUser("feedback-concurrent-author@test.com")
        val admin = persistUser("feedback-concurrent-admin@test.com", Role.ADMIN)
        val firstVoter = persistUser("feedback-concurrent-first-voter@test.com")
        val secondVoter = persistUser("feedback-concurrent-second-voter@test.com")
        val firstSourceId = createFeedback(author, "First duplicate")
        val secondSourceId = createFeedback(author, "Second duplicate")
        val targetId = createFeedback(author, "Canonical")

        mockMvc.perform(post("/api/v1/feedback/$firstSourceId/votes").withAuth(firstVoter.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/feedback/$secondSourceId/votes").withAuth(secondVoter.id!!, Role.USER))
            .andExpect(status().isOk)

        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val first = executor.submit<Int> {
                start.await()
                mergeFeedback(firstSourceId, targetId, admin).andReturn().response.status
            }
            val second = executor.submit<Int> {
                start.await()
                mergeFeedback(secondSourceId, targetId, admin).andReturn().response.status
            }
            start.countDown()

            assertEquals(200, first.get(10, TimeUnit.SECONDS))
            assertEquals(200, second.get(10, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }

        mockMvc.perform(get("/api/v1/feedback/$targetId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.voteCount").value(2))
    }

    @Test
    fun `writers cannot modify feedback after it is merged`() {
        val author = persistUser("feedback-merged-writer-author@test.com")
        val admin = persistUser("feedback-merged-writer-admin@test.com", Role.ADMIN)
        val existingVoter = persistUser("feedback-merged-writer-existing@test.com")
        val newVoter = persistUser("feedback-merged-writer-new@test.com")
        val sourceId = createFeedback(author, "Source")
        val targetId = createFeedback(author, "Target")
        mockMvc.perform(post("/api/v1/feedback/$sourceId/votes").withAuth(existingVoter.id!!, Role.USER))
            .andExpect(status().isOk)

        mergeFeedback(sourceId, targetId, admin).andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/feedback/$sourceId/votes").withAuth(newVoter.id!!, Role.USER))
            .andExpect(status().isConflict)
        mockMvc.perform(delete("/api/v1/feedback/$sourceId/votes").withAuth(existingVoter.id!!, Role.USER))
            .andExpect(status().isConflict)
        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/comments")
                .withAuth(author.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateFeedbackCommentRequest("Late comment")))
        ).andExpect(status().isConflict)
    }

    @Test
    fun `merge and vote serialize without leaving votes on source`() {
        val author = persistUser("feedback-merge-vote-author@test.com")
        val admin = persistUser("feedback-merge-vote-admin@test.com", Role.ADMIN)
        val voter = persistUser("feedback-merge-vote-voter@test.com")
        val sourceId = createFeedback(author, "Source")
        val targetId = createFeedback(author, "Target")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val voteStatus: Int
        try {
            val merge = executor.submit<Int> {
                start.await()
                mergeFeedback(sourceId, targetId, admin).andReturn().response.status
            }
            val vote = executor.submit<Int> {
                start.await()
                mockMvc.perform(post("/api/v1/feedback/$sourceId/votes").withAuth(voter.id!!, Role.USER))
                    .andReturn().response.status
            }
            start.countDown()

            assertEquals(200, merge.get(10, TimeUnit.SECONDS))
            voteStatus = vote.get(10, TimeUnit.SECONDS)
            assertEquals(true, voteStatus in setOf(200, 409))
        } finally {
            executor.shutdownNow()
        }

        mockMvc.perform(get("/api/v1/feedback/$sourceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.voteCount").value(0))
        mockMvc.perform(get("/api/v1/feedback/$targetId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.voteCount").value(if (voteStatus == 200) 1 else 0))
    }

    @Test
    fun `merge and update serialize without restoring source state after merge`() {
        val author = persistUser("feedback-merge-update-author@test.com")
        val admin = persistUser("feedback-merge-update-admin@test.com", Role.ADMIN)
        val sourceId = createFeedback(author, "Source")
        val targetId = createFeedback(author, "Target")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val updateStatus: Int
        try {
            val merge = executor.submit<Int> {
                start.await()
                mergeFeedback(sourceId, targetId, admin).andReturn().response.status
            }
            val update = executor.submit<Int> {
                start.await()
                mockMvc.perform(
                    patch("/api/v1/feedback/$sourceId")
                        .withAuth(author.id!!, Role.USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateFeedbackRequest(title = "Late update")))
                ).andReturn().response.status
            }
            start.countDown()

            assertEquals(200, merge.get(10, TimeUnit.SECONDS))
            updateStatus = update.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertEquals(true, updateStatus in setOf(200, 409))
        mockMvc.perform(get("/api/v1/feedback/$sourceId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mergedIntoId").value(targetId))
            .andExpect(jsonPath("$.status").value("declined"))
    }

    @Test
    fun `merge and target delete serialize without server error`() {
        val author = persistUser("feedback-merge-delete-author@test.com")
        val admin = persistUser("feedback-merge-delete-admin@test.com", Role.ADMIN)
        val sourceId = createFeedback(author, "Source")
        val targetId = createFeedback(author, "Target")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val mergeStatus: Int
        val deleteStatus: Int
        try {
            val merge = executor.submit<Int> {
                start.await()
                mergeFeedback(sourceId, targetId, admin).andReturn().response.status
            }
            val delete = executor.submit<Int> {
                start.await()
                mockMvc.perform(delete("/api/v1/feedback/$targetId").withAuth(admin.id!!, Role.ADMIN))
                    .andReturn().response.status
            }
            start.countDown()
            mergeStatus = merge.get(10, TimeUnit.SECONDS)
            deleteStatus = delete.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertEquals(true, (mergeStatus == 200 && deleteStatus == 409) || (mergeStatus == 404 && deleteStatus == 204))
    }

    @Test
    fun `feedback audit is visible only to admins`() {
        val author = persistUser("feedback-audit-author@test.com")
        val admin = persistUser("feedback-audit-admin@test.com", Role.ADMIN)
        val id = createFeedback(author, "Audited feedback")

        mockMvc.perform(get("/api/v1/feedback/$id?include=audit"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(get("/api/v1/feedback/$id?include=audit").withAuth(author.id!!, Role.USER))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/feedback/$id?include=audit").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.audit").isArray)
            .andExpect(jsonPath("$.audit[0].rowId").value(id))
    }

    private fun createFeedback(author: Users, title: String, body: String = "Feedback body"): String =
        objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/feedback")
                    .withAuth(author.id!!, author.role)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(CreateFeedbackRequest("idea", title, body)))
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        ).get("id").asText()

    private fun mergeFeedback(sourceId: String, targetId: String, admin: Users) =
        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/merge")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MergeFeedbackRequest(UUID.fromString(targetId))))
        )

    private fun persistUser(email: String, role: Role = Role.USER): Users =
        usersRepository.save(
            Users(
                email = email,
                role = role,
                createdAt = Instant.now()
            )
        )
}
