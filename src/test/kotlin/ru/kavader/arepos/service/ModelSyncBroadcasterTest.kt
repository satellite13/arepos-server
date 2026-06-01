package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.springframework.messaging.simp.SimpMessagingTemplate
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.model.ModelSyncOutbox
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelSyncOutboxRepository
import ru.kavader.arepos.repository.ModelsRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class ModelSyncBroadcasterTest {

    @Mock
    lateinit var messagingTemplate: SimpMessagingTemplate

    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var outboxRepository: ModelSyncOutboxRepository

    private val objectMapper = ObjectMapper()

    private fun newBroadcaster(outbox: Boolean): ModelSyncBroadcaster =
        ModelSyncBroadcaster(
            messagingTemplate,
            modelsRepository,
            objectMapper,
            outboxRepository,
            outbox
        )

    @Test
    fun `broadcastModelChanged sends v2 payload with eventId modelRevision and events`() {
        val broadcaster = newBroadcaster(outbox = false)
        val modelId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        val owner = Users(
            email = "o@test.com",
            createdAt = Instant.now()
        )
        val model = Models(
            id = modelId,
            name = "m",
            createdAt = Instant.now(),
            version = "1.0.0",
            owner = owner,
            syncRevision = 7L
        )
        `when`(modelsRepository.incrementSyncRevision(modelId)).thenReturn(1)
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))

        broadcaster.broadcastModelChanged(
            modelId,
            "unit",
            listOf(ModelSyncEntityEvent("node_updated", "node", nodeId))
        )

        val captor = ArgumentCaptor.forClass(Map::class.java)
        verify(messagingTemplate).convertAndSend(eq("/topic/models/$modelId"), captor.capture())
        @Suppress("UNCHECKED_CAST")
        val payload = captor.value as Map<String, Any?>
        assertEquals(2, payload["v"])
        assertEquals("model_changed", payload["type"])
        assertNotNull(payload["eventId"])
        assertEquals(7L, (payload["modelRevision"] as Number).toLong())
        assertEquals(modelId.toString(), payload["modelId"])
        @Suppress("UNCHECKED_CAST")
        val events = payload["events"] as List<Map<String, Any>>
        assertEquals(1, events.size)
        assertEquals("node_updated", events[0]["type"])
        assertEquals("node", events[0]["entity"])
        assertEquals(nodeId.toString(), events[0]["id"])
        assertEquals(7L, (events[0]["revision"] as Number).toLong())
    }

    @Test
    fun `broadcastModelChanged with outbox persists row instead of stomp`() {
        val broadcaster = newBroadcaster(outbox = true)
        val modelId = UUID.randomUUID()
        val owner = Users(email = "o2@test.com", createdAt = Instant.now())
        val model = Models(
            id = modelId,
            name = "m2",
            createdAt = Instant.now(),
            version = "1.0.0",
            owner = owner,
            syncRevision = 1L
        )
        `when`(modelsRepository.incrementSyncRevision(modelId)).thenReturn(1)
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))
        `when`(outboxRepository.save(any(ModelSyncOutbox::class.java)))
            .thenAnswer { inv: InvocationOnMock -> inv.getArgument(0, ModelSyncOutbox::class.java) }

        broadcaster.broadcastModelChanged(modelId, "unit", emptyList())

        verify(outboxRepository).save(any(ModelSyncOutbox::class.java))
        verify(messagingTemplate, never()).convertAndSend(any(String::class.java), any(Any::class.java))
    }
}
