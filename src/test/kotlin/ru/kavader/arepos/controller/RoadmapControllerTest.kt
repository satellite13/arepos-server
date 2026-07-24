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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.site.CreateFeedbackRequest
import ru.kavader.arepos.dto.site.CreateRoadmapMilestoneRequest
import ru.kavader.arepos.dto.site.MergeFeedbackRequest
import ru.kavader.arepos.dto.site.SetRoadmapMilestoneItemsRequest
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

    @Test
    fun `set items rejects feedback that was merged`() {
        val admin = persistUser("roadmap-merged-admin@test.com", Role.ADMIN)
        val author = persistUser("roadmap-merged-author@test.com")
        val sourceId = createFeedback(author, "Merged source")
        val targetId = createFeedback(author, "Merge target")
        val milestoneId = createMilestone(admin, "Merged feedback")

        mergeFeedback(sourceId, targetId, admin).andExpect(status().isOk)

        setItems(milestoneId, listOf(sourceId), admin)
            .andExpect(status().isConflict)
    }

    @Test
    fun `set items can replace composition keeping overlapping feedback`() {
        val admin = persistUser("roadmap-replace-admin@test.com", Role.ADMIN)
        val author = persistUser("roadmap-replace-author@test.com")
        val keepId = createFeedback(author, "Keep linked")
        val removeId = createFeedback(author, "Remove linked")
        val addId = createFeedback(author, "Add linked")
        val milestoneId = createMilestone(admin, "Replace composition")

        setItems(milestoneId, listOf(keepId, removeId), admin)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))

        setItems(milestoneId, listOf(keepId, addId), admin)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[?(@.id == '$keepId')]").exists())
            .andExpect(jsonPath("$.items[?(@.id == '$addId')]").exists())
            .andExpect(jsonPath("$.items[?(@.id == '$removeId')]").doesNotExist())
    }

    @Test
    fun `set items can reapply the same feedback ids`() {
        val admin = persistUser("roadmap-reapply-admin@test.com", Role.ADMIN)
        val author = persistUser("roadmap-reapply-author@test.com")
        val feedbackId = createFeedback(author, "Same link")
        val milestoneId = createMilestone(admin, "Reapply composition")

        setItems(milestoneId, listOf(feedbackId), admin).andExpect(status().isOk)
        setItems(milestoneId, listOf(feedbackId), admin)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(feedbackId.toString()))
            .andExpect(jsonPath("$.items[0].publicKey").value(org.hamcrest.Matchers.matchesPattern("FB-\\d+")))
    }

    @Test
    fun `merge and set items never leave stale source roadmap link`() {
        val admin = persistUser("roadmap-concurrent-admin@test.com", Role.ADMIN)
        val author = persistUser("roadmap-concurrent-author@test.com")
        val sourceId = createFeedback(author, "Concurrent source")
        val targetId = createFeedback(author, "Concurrent target")
        val milestoneId = createMilestone(admin, "Concurrent merge")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val mergeStatus: Int
        val setItemsStatus: Int
        try {
            val merge = executor.submit<Int> {
                start.await()
                mergeFeedback(sourceId, targetId, admin).andReturn().response.status
            }
            val setItems = executor.submit<Int> {
                start.await()
                setItems(milestoneId, listOf(sourceId), admin).andReturn().response.status
            }
            start.countDown()
            mergeStatus = merge.get(10, TimeUnit.SECONDS)
            setItemsStatus = setItems.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        org.junit.jupiter.api.Assertions.assertEquals(200, mergeStatus)
        org.junit.jupiter.api.Assertions.assertEquals(true, setItemsStatus in setOf(200, 409))
        mockMvc.perform(get("/api/v1/roadmap/milestones/$milestoneId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.id == '$sourceId')]").isEmpty)
    }

    @Test
    fun `admin receives conflict when updating milestone with stale timestamp`() {
        val admin = persistUser("roadmap-update-admin@test.com", Role.ADMIN)
        val created = createMilestoneResponse(admin, "Before")
        val milestoneId = UUID.fromString(created.get("id").asText())
        val staleTimestamp = created.get("updatedAt").asText()

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/$milestoneId")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "Changed", "baseUpdatedAt" to staleTimestamp)))
        ).andExpect(status().isOk)

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/$milestoneId")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "Stale", "baseUpdatedAt" to staleTimestamp)))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("ROADMAP_UPDATE_CONFLICT"))
            .andExpect(jsonPath("$.conflicts[0].id").value(milestoneId.toString()))
            .andExpect(jsonPath("$.conflicts[0].serverUpdatedAt").exists())
    }

    @Test
    fun `update conflicts when timestamp differs by one microsecond within millisecond`() {
        val admin = persistUser("roadmap-microsecond-conflict-admin@test.com", Role.ADMIN)
        val milestone = createMilestoneResponse(admin, "Microsecond precision")
        val milestoneId = milestone.get("id").asText()
        val clientTimestamp = Instant.parse(milestone.get("updatedAt").asText())
            .plusNanos(1_000)
            .toString()

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/$milestoneId")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "Must conflict", "baseUpdatedAt" to clientTimestamp)))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("ROADMAP_UPDATE_CONFLICT"))
            .andExpect(jsonPath("$.conflicts[0].id").value(milestoneId))
    }

    @Test
    fun `admin must supply base timestamp when updating milestone`() {
        val admin = persistUser("roadmap-missing-update-timestamp-admin@test.com", Role.ADMIN)
        val milestone = createMilestoneResponse(admin, "Timestamp required")

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/${milestone.get("id").asText()}")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "Changed")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `omitted target period is preserved while explicit null clears it`() {
        val admin = persistUser("roadmap-clear-period-admin@test.com", Role.ADMIN)
        val milestone = objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/roadmap/milestones")
                    .withAuth(admin.id!!, Role.ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            CreateRoadmapMilestoneRequest("Scheduled", targetPeriod = "2026 Q3")
                        )
                    )
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        )
        val id = milestone.get("id").asText()

        val preserved = mockMvc.perform(
            put("/api/v1/roadmap/milestones/$id")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("title" to "Still scheduled", "baseUpdatedAt" to milestone.get("updatedAt").asText())
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetPeriod").value("2026 Q3"))
            .andReturn()

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/$id")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "targetPeriod" to null,
                            "baseUpdatedAt" to objectMapper.readTree(preserved.response.contentAsString).get("updatedAt").asText()
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetPeriod").value(null))
    }

    @Test
    fun `roadmap audit is visible only to admins`() {
        val admin = persistUser("roadmap-audit-admin@test.com", Role.ADMIN)
        val user = persistUser("roadmap-audit-user@test.com")
        val id = createMilestone(admin, "Audited roadmap")

        mockMvc.perform(get("/api/v1/roadmap/milestones/$id?include=audit").withAuth(user.id!!, Role.USER))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/roadmap/milestones/$id?include=audit").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.audit").isArray)
            .andExpect(jsonPath("$.audit[0].rowId").value(id.toString()))
    }

    @Test
    fun `delete and set items serialize with a clean conflict`() {
        val admin = persistUser("roadmap-delete-set-items-admin@test.com", Role.ADMIN)
        val author = persistUser("roadmap-delete-set-items-author@test.com")
        val feedbackId = createFeedback(author, "Concurrent feedback")
        val milestoneId = createMilestone(admin, "Concurrent delete")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val deleteStatus: Int
        val setItemsStatus: Int
        try {
            val delete = executor.submit<Int> {
                start.await()
                mockMvc.perform(delete("/api/v1/roadmap/milestones/$milestoneId").withAuth(admin.id!!, Role.ADMIN))
                    .andReturn().response.status
            }
            val setItems = executor.submit<Int> {
                start.await()
                setItems(milestoneId, listOf(feedbackId), admin).andReturn().response.status
            }
            start.countDown()
            deleteStatus = delete.get(10, TimeUnit.SECONDS)
            setItemsStatus = setItems.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        org.junit.jupiter.api.Assertions.assertEquals(204, deleteStatus)
        org.junit.jupiter.api.Assertions.assertEquals(true, setItemsStatus in setOf(200, 409))
        mockMvc.perform(get("/api/v1/roadmap/milestones/$milestoneId")).andExpect(status().isNotFound)
    }

    @Test
    fun `admin atomically reorders milestones with matching timestamps`() {
        val admin = persistUser("roadmap-reorder-admin@test.com", Role.ADMIN)
        val first = createMilestoneResponse(admin, "First", 1)
        val second = createMilestoneResponse(admin, "Second", 2)
        val untouched = createMilestoneResponse(admin, "Untouched", 3)

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/order")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "items" to listOf(
                                mapOf(
                                    "id" to first.get("id").asText(),
                                    "sortOrder" to 2,
                                    "baseUpdatedAt" to first.get("updatedAt").asText()
                                ),
                                mapOf(
                                    "id" to second.get("id").asText(),
                                    "sortOrder" to 1,
                                    "baseUpdatedAt" to second.get("updatedAt").asText()
                                )
                            )
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(second.get("id").asText()))
            .andExpect(jsonPath("$[0].sortOrder").value(1))
            .andExpect(jsonPath("$[1].id").value(first.get("id").asText()))
            .andExpect(jsonPath("$[1].sortOrder").value(2))
            .andExpect(jsonPath("$[2].id").value(untouched.get("id").asText()))
            .andExpect(jsonPath("$[2].sortOrder").value(3))
    }

    @Test
    fun `non-admin cannot reorder milestones`() {
        val admin = persistUser("roadmap-order-owner@test.com", Role.ADMIN)
        val user = persistUser("roadmap-order-user@test.com")
        val milestone = createMilestoneResponse(admin, "Protected")

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/order")
                .withAuth(user.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "items" to listOf(
                                mapOf(
                                    "id" to milestone.get("id").asText(),
                                    "sortOrder" to 1,
                                    "baseUpdatedAt" to milestone.get("updatedAt").asText()
                                )
                            )
                        )
                    )
                )
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin receives conflict when reordering with stale timestamp`() {
        val admin = persistUser("roadmap-stale-order-admin@test.com", Role.ADMIN)
        val first = createMilestoneResponse(admin, "First", 1)
        val second = createMilestoneResponse(admin, "Second", 2)
        val staleTimestamp = first.get("updatedAt").asText()
        val firstId = UUID.fromString(first.get("id").asText())

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/$firstId")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "Updated", "baseUpdatedAt" to staleTimestamp)))
        ).andExpect(status().isOk)

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/order")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "items" to listOf(
                                mapOf("id" to firstId.toString(), "sortOrder" to 2, "baseUpdatedAt" to staleTimestamp),
                                mapOf(
                                    "id" to second.get("id").asText(),
                                    "sortOrder" to 1,
                                    "baseUpdatedAt" to second.get("updatedAt").asText()
                                )
                            )
                        )
                    )
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("ROADMAP_ORDER_CONFLICT"))
            .andExpect(jsonPath("$.conflicts[0].id").value(firstId.toString()))
            .andExpect(jsonPath("$.conflicts[0].serverUpdatedAt").exists())
    }

    @Test
    fun `reverse-order concurrent reorders serialize without deadlock`() {
        val admin = persistUser("roadmap-reorder-lock-admin@test.com", Role.ADMIN)
        val first = createMilestoneResponse(admin, "First", 1)
        val second = createMilestoneResponse(admin, "Second", 2)
        val forwardItems = listOf(
            mapOf("id" to first.get("id").asText(), "sortOrder" to 1, "baseUpdatedAt" to first.get("updatedAt").asText()),
            mapOf("id" to second.get("id").asText(), "sortOrder" to 2, "baseUpdatedAt" to second.get("updatedAt").asText())
        )
        val reverseItems = listOf(
            mapOf("id" to second.get("id").asText(), "sortOrder" to 1, "baseUpdatedAt" to second.get("updatedAt").asText()),
            mapOf("id" to first.get("id").asText(), "sortOrder" to 2, "baseUpdatedAt" to first.get("updatedAt").asText())
        )
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val forwardStatus: Int
        val reverseStatus: Int
        try {
            val forward = executor.submit<Int> {
                start.await()
                reorder(admin, forwardItems).andReturn().response.status
            }
            val reverse = executor.submit<Int> {
                start.await()
                reorder(admin, reverseItems).andReturn().response.status
            }
            start.countDown()
            forwardStatus = forward.get(10, TimeUnit.SECONDS)
            reverseStatus = reverse.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        org.junit.jupiter.api.Assertions.assertEquals(setOf(200, 409), setOf(forwardStatus, reverseStatus))
        mockMvc.perform(get("/api/v1/roadmap"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].sortOrder").value(1))
            .andExpect(jsonPath("$[1].sortOrder").value(2))
    }

    @Test
    fun `reorder rejects duplicate milestone IDs`() {
        val admin = persistUser("roadmap-duplicate-id-admin@test.com", Role.ADMIN)
        val milestone = createMilestoneResponse(admin, "Duplicate id")

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/order")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "items" to listOf(
                                mapOf(
                                    "id" to milestone.get("id").asText(),
                                    "sortOrder" to 1,
                                    "baseUpdatedAt" to milestone.get("updatedAt").asText()
                                ),
                                mapOf(
                                    "id" to milestone.get("id").asText(),
                                    "sortOrder" to 2,
                                    "baseUpdatedAt" to milestone.get("updatedAt").asText()
                                )
                            )
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `reorder rejects duplicate sort orders`() {
        val admin = persistUser("roadmap-duplicate-order-admin@test.com", Role.ADMIN)
        val first = createMilestoneResponse(admin, "First")
        val second = createMilestoneResponse(admin, "Second")

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/order")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "items" to listOf(
                                mapOf(
                                    "id" to first.get("id").asText(),
                                    "sortOrder" to 1,
                                    "baseUpdatedAt" to first.get("updatedAt").asText()
                                ),
                                mapOf(
                                    "id" to second.get("id").asText(),
                                    "sortOrder" to 1,
                                    "baseUpdatedAt" to second.get("updatedAt").asText()
                                )
                            )
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `reorder rejects missing milestone ID`() {
        val admin = persistUser("roadmap-missing-id-admin@test.com", Role.ADMIN)

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/order")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "items" to listOf(
                                mapOf(
                                    "id" to UUID.randomUUID().toString(),
                                    "sortOrder" to 1,
                                    "baseUpdatedAt" to Instant.now().toString()
                                )
                            )
                        )
                    )
                )
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }

    @Test
    fun `reorder rejects missing required timestamp`() {
        val admin = persistUser("roadmap-missing-order-timestamp-admin@test.com", Role.ADMIN)
        val milestone = createMilestoneResponse(admin, "Timestamp required")

        mockMvc.perform(
            put("/api/v1/roadmap/milestones/order")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "items" to listOf(
                                mapOf("id" to milestone.get("id").asText(), "sortOrder" to 1)
                            )
                        )
                    )
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    private fun createFeedback(author: Users, title: String): UUID =
        UUID.fromString(
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
                ).andExpect(status().isCreated).andReturn().response.contentAsString
            ).get("id").asText()
        )

    private fun createMilestone(admin: Users, title: String): UUID =
        UUID.fromString(createMilestoneResponse(admin, title).get("id").asText())

    private fun createMilestoneResponse(admin: Users, title: String, sortOrder: Int = 0) =
        objectMapper.readTree(
            mockMvc.perform(
                post("/api/v1/roadmap/milestones")
                    .withAuth(admin.id!!, Role.ADMIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(CreateRoadmapMilestoneRequest(title, sortOrder = sortOrder)))
            ).andExpect(status().isCreated).andReturn().response.contentAsString
        )

    private fun reorder(admin: Users, items: List<Map<String, Any>>) =
        mockMvc.perform(
            put("/api/v1/roadmap/milestones/order")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("items" to items)))
        )

    private fun mergeFeedback(sourceId: UUID, targetId: UUID, admin: Users) =
        mockMvc.perform(
            post("/api/v1/feedback/$sourceId/merge")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(MergeFeedbackRequest(targetId)))
        )

    private fun setItems(milestoneId: UUID, feedbackIds: List<UUID>, admin: Users) =
        mockMvc.perform(
            put("/api/v1/roadmap/milestones/$milestoneId/items")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(SetRoadmapMilestoneItemsRequest(feedbackIds)))
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
