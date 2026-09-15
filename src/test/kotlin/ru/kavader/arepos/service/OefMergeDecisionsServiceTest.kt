package ru.kavader.arepos.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.oef.OefMergeDecisionSaveItem
import ru.kavader.arepos.dto.oef.OefMergeDecisionSaveRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.OefMergeDecisions
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.OefMergeDecisionsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OefMergeDecisionsServiceTest {

    private val modelsRepository = mock(ModelsRepository::class.java)
    private val nodesRepository = mock(NodesRepository::class.java)
    private val decisionsRepository = mock(OefMergeDecisionsRepository::class.java)
    private val usersRepository = mock(UsersRepository::class.java)
    private val accessService = mock(ResourceAccessService::class.java)

    private val service = OefMergeDecisionsService(
        modelsRepository = modelsRepository,
        nodesRepository = nodesRepository,
        decisionsRepository = decisionsRepository,
        usersRepository = usersRepository,
        accessService = accessService,
    )

    private val currentUserId = UUID.randomUUID()

    private val modelId = UUID.randomUUID()
    private val model = Models(name = "m", version = "1.0.0", owner = Users(email = "o@x.io"))

    @BeforeEach
    fun setUp() {
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(
            currentUserId, null, "ROLE_USER"
        )
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun node(id: UUID): Nodes = Nodes(
        stableId = UUID.randomUUID(),
        name = "node-$id",
        createdAt = Instant.now(),
        model = model,
        owner = model.owner,
        nodeType = mock(NodeTypes::class.java),
    ).also {
        val field = Nodes::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(it, id)
    }

    @Test
    fun `saves new decision and skips empty entity ids`() {
        val targetId = UUID.randomUUID()
        val target = node(targetId)
        `when`(nodesRepository.findByModel_IdAndIdIn(modelId, listOf(targetId)))
            .thenReturn(listOf(target))
        `when`(usersRepository.findById(currentUserId)).thenReturn(Optional.empty())
        `when`(
            decisionsRepository.findByModelAndOefEntityId(model, "el-1")
        ).thenReturn(null)

        val response = service.save(
            modelId,
            OefMergeDecisionSaveRequest(
                decisions = listOf(
                    OefMergeDecisionSaveItem(oefEntityId = "el-1", targetNodeId = targetId),
                    OefMergeDecisionSaveItem(oefEntityId = "  ", targetNodeId = targetId),
                )
            )
        )

        assertEquals(1, response.saved)
        assertEquals(1, response.skipped)
        val captor = org.mockito.ArgumentCaptor.forClass(OefMergeDecisions::class.java)
        verify(decisionsRepository).save(captor.capture())
        assertEquals("el-1", captor.value.oefEntityId)
        assertEquals(target, captor.value.targetNode)
        assertNotNull(captor.value.createdAt)
        assertNull(captor.value.updatedAt)
    }

    @Test
    fun `existing decision is repointed to the new target`() {
        val targetId = UUID.randomUUID()
        val target = node(targetId)
        val existing = OefMergeDecisions(
            model = model,
            oefEntityId = "el-1",
            targetNode = node(UUID.randomUUID()),
            createdAt = Instant.now(),
        )
        `when`(nodesRepository.findByModel_IdAndIdIn(modelId, listOf(targetId)))
            .thenReturn(listOf(target))
        `when`(usersRepository.findById(currentUserId)).thenReturn(Optional.empty())
        `when`(
            decisionsRepository.findByModelAndOefEntityId(model, "el-1")
        ).thenReturn(existing)

        val response = service.save(
            modelId,
            OefMergeDecisionSaveRequest(
                decisions = listOf(
                    OefMergeDecisionSaveItem(oefEntityId = "el-1", targetNodeId = targetId),
                )
            )
        )

        assertEquals(1, response.saved)
        assertEquals(0, response.skipped)
        assertEquals(target, existing.targetNode)
        assertNotNull(existing.updatedAt)
        verify(decisionsRepository).save(existing)
    }

    @Test
    fun `decision with unknown target node is skipped`() {
        val targetId = UUID.randomUUID()
        `when`(nodesRepository.findByModel_IdAndIdIn(modelId, listOf(targetId)))
            .thenReturn(emptyList())

        val response = service.save(
            modelId,
            OefMergeDecisionSaveRequest(
                decisions = listOf(
                    OefMergeDecisionSaveItem(oefEntityId = "el-1", targetNodeId = targetId),
                )
            )
        )

        assertEquals(0, response.saved)
        assertEquals(1, response.skipped)
        verify(decisionsRepository, never())
            .save(org.mockito.ArgumentMatchers.any(OefMergeDecisions::class.java))
    }

    @Test
    fun `empty decision list is a no-op`() {
        val response = service.save(modelId, OefMergeDecisionSaveRequest(decisions = emptyList()))
        assertEquals(0, response.saved)
        assertEquals(0, response.skipped)
    }

    @Test
    fun `unknown model returns 404`() {
        val missing = UUID.randomUUID()
        `when`(modelsRepository.findById(missing)).thenReturn(Optional.empty())

        val ex = runCatching { service.list(missing) }.exceptionOrNull()
        assertTrue(ex is ResponseStatusException)
        assertEquals(HttpStatus.NOT_FOUND, (ex as ResponseStatusException).statusCode)
    }
}
