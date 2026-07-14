package ru.kavader.arepos.controller

import com.fasterxml.jackson.core.type.TypeReference
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
import ru.kavader.arepos.dto.model.DiagramRequest
import ru.kavader.arepos.dto.model.DiagramResponse
import ru.kavader.arepos.dto.model.DiagramUpdateRequest
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ADMIN_ONLY
import ru.kavader.arepos.security.DIAGRAM_LOCK_HELD_BY_ANOTHER_USER
import ru.kavader.arepos.security.DIAGRAM_LOCK_REQUIRED
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class DiagramEditLocksControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var diagramEditLocksRepository: DiagramEditLocksRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    private data class Setup(
        val ownerId: UUID,
        val modelId: UUID,
        val diagramId: UUID
    )

    private fun createDiagramFixture(): Setup {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-owner-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "model-lock-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "notation-lock-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        val nodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "nt-lock-${UUID.randomUUID()}",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        val node = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = UUID.randomUUID(),
                name = "node-lock",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now()
            )
        )
        val payload = DiagramRequest(
            name = "diagram-lock-${UUID.randomUUID()}",
            version = "1.0.0",
            ownerId = owner.id!!,
            modelId = model.id!!,
            nodeId = node.id!!,
            notationId = notation.id!!,
            attrs = """{"layout":"auto"}"""
        )
        val createdJson = mockMvc.perform(
            post("/api/v1/diagrams")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        val created = objectMapper.readValue(createdJson, DiagramResponse::class.java)
        return Setup(owner.id!!, model.id!!, created.id)
    }

    @Test
    fun `acquire returns 200 and idempotent refresh for same user`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isLocked").value(true))
            .andExpect(jsonPath("$.lockedByUserId").value(s.ownerId.toString()))

        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lockedByUserId").value(s.ownerId.toString()))
    }

    @Test
    fun `second user gets 409 when lock held by other`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val other = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-other-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val now = Instant.now()
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = s.modelId,
                granteeUser = other,
                grantedByUser = usersRepository.findById(s.ownerId).get(),
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(other.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isLocked").value(true))
            .andExpect(jsonPath("$.reason").value("LOCKED_BY_OTHER"))
            .andExpect(jsonPath("$.lockedByUserId").value(s.ownerId.toString()))
            .andExpect(jsonPath("$.lockedByDisplay").isNotEmpty)
    }

    @Test
    fun `release then other user can acquire`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val other = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-other2-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val now = Instant.now()
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = s.modelId,
                granteeUser = other,
                grantedByUser = usersRepository.findById(s.ownerId).get(),
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/release").withAuth(s.ownerId))
            .andExpect(status().isNoContent)

        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(other.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lockedByUserId").value(other.id.toString()))
    }

    @Test
    fun `GET list by modelId returns active lock`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val json = mockMvc.perform(
            get("/api/v1/diagram-locks").param("modelId", s.modelId.toString()).withAuth(s.ownerId)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val envelope = objectMapper.readTree(json)
        val list = objectMapper.convertValue(
            envelope.path("items"),
            object : TypeReference<List<Map<String, Any?>>>() {}
        )
        assertEquals(1, list.size)
        assertEquals(s.diagramId.toString(), list[0]["diagramId"])
        assertEquals(true, list[0]["isLocked"])
    }

    @Test
    fun `admin force-release clears lock`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-admin-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/force-release").withAuth(admin.id!!))
            .andExpect(status().isNoContent)

        val json = mockMvc.perform(
            get("/api/v1/diagram-locks").param("modelId", s.modelId.toString()).withAuth(s.ownerId)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val envelope = objectMapper.readTree(json)
        val list = objectMapper.convertValue(envelope.path("items"), object : TypeReference<List<Any>>() {})
        assertTrue(list.isEmpty())
    }

    @Test
    fun `force-release returns admin-only message to regular user`() {
        val user = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-regular-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(post("/api/v1/diagram-locks/${UUID.randomUUID()}/force-release").withAuth(user.id!!, Role.USER))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value(ADMIN_ONLY))
    }

    @Test
    fun `live update without active lock returns lock-required message`() {
        val s = createDiagramFixture()

        mockMvc.perform(
            post("/api/v1/diagram-locks/${s.diagramId}/live")
                .withAuth(s.ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value(DIAGRAM_LOCK_REQUIRED))
    }

    @Test
    fun `heartbeat by holder returns 200`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/heartbeat").withAuth(s.ownerId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isLocked").value(true))
            .andExpect(jsonPath("$.lockedByUserId").value(s.ownerId.toString()))
    }

    @Test
    fun `heartbeat increments lock entity version`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val before = diagramEditLocksRepository.findByDiagramId(s.diagramId)
        assertNotNull(before)
        val versionBefore = before.version

        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/heartbeat").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val after = diagramEditLocksRepository.findByDiagramId(s.diagramId)
        assertNotNull(after)
        assertTrue(
            after.version > versionBefore,
            "Expected lock version to increase after heartbeat, before=$versionBefore after=${after.version}"
        )
    }

    @Test
    fun `heartbeat by non-holder returns 403`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val other = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-heartbeat-other-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val now = Instant.now()
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = s.modelId,
                granteeUser = other,
                grantedByUser = usersRepository.findById(s.ownerId).get(),
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/heartbeat").withAuth(other.id!!, Role.USER))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value(DIAGRAM_LOCK_HELD_BY_ANOTHER_USER))
    }

    @Test
    fun `release by non-holder returns lock-held-by-another-user message`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val other = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-release-other-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val now = Instant.now()
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = s.modelId,
                granteeUser = other,
                grantedByUser = usersRepository.findById(s.ownerId).get(),
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/release").withAuth(other.id!!, Role.USER))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value(DIAGRAM_LOCK_HELD_BY_ANOTHER_USER))
    }

    @Test
    fun `GET list without modelId returns 400 for non-admin`() {
        val s = createDiagramFixture()
        val other = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-list-user-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val now = Instant.now()
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = s.modelId,
                granteeUser = other,
                grantedByUser = usersRepository.findById(s.ownerId).get(),
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(get("/api/v1/diagram-locks").withAuth(other.id!!, Role.USER))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET list without modelId returns all active locks for admin`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "lock-list-admin-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val json = mockMvc.perform(get("/api/v1/diagram-locks").withAuth(admin.id!!))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val envelope = objectMapper.readTree(json)
        val list = objectMapper.convertValue(
            envelope.path("items"),
            object : TypeReference<List<Map<String, Any?>>>() {}
        )
        assertEquals(1, list.size)
        assertEquals(s.diagramId.toString(), list[0]["diagramId"])
    }

    @Test
    fun `GET list exposes diagramUpdatedAt refreshed after diagram save`() {
        val s = createDiagramFixture()
        mockMvc.perform(post("/api/v1/diagram-locks/${s.diagramId}/acquire").withAuth(s.ownerId))
            .andExpect(status().isOk)

        val lockJsonBefore = mockMvc.perform(
            get("/api/v1/diagram-locks").param("modelId", s.modelId.toString()).withAuth(s.ownerId)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val listBefore =
            objectMapper.convertValue(
                objectMapper.readTree(lockJsonBefore).path("items"),
                object : TypeReference<List<Map<String, Any?>>>() {}
            )
        assertEquals(1, listBefore.size)
        val atBefore = listBefore[0]["diagramUpdatedAt"] as String?
        assertNotNull(atBefore)
        val instantBefore = Instant.parse(atBefore)

        mockMvc.perform(
            put("/api/v1/diagrams/${s.diagramId}")
                .withAuth(s.ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        DiagramUpdateRequest(attrs = """{"layout":"auto","testPatch":true}""")
                    )
                )
        )
            .andExpect(status().isOk)

        val lockJsonAfter = mockMvc.perform(
            get("/api/v1/diagram-locks").param("modelId", s.modelId.toString()).withAuth(s.ownerId)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val listAfter =
            objectMapper.convertValue(
                objectMapper.readTree(lockJsonAfter).path("items"),
                object : TypeReference<List<Map<String, Any?>>>() {}
            )
        assertEquals(1, listAfter.size)
        val atAfter = listAfter[0]["diagramUpdatedAt"] as String?
        assertNotNull(atAfter)
        val instantAfter = Instant.parse(atAfter)
        assertTrue(
            !instantAfter.isBefore(instantBefore),
            "diagramUpdatedAt in lock list should reflect diagram row after save"
        )
    }
}
