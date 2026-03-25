package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.dto.DiagramSpectatorView
import ru.kavader.arepos.dto.ModelSyncEntityEvent
import ru.kavader.arepos.model.ModelSyncOutbox
import ru.kavader.arepos.repository.ModelSyncOutboxRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.CurrentUser
import java.time.Instant
import java.util.UUID

/**
 * Push-уведомление подписчикам модели: пора обновить снимок (дерево / связи / диаграммы).
 * Транспорт: STOMP topic `/topic/models/{modelId}`.
 *
 * Payload `model_changed` (v=2): `eventId`, `modelRevision`, опционально `events` (granular).
 * При [outboxEnabled] запись в outbox вместо прямой отправки (публикация — [ModelSyncOutboxPublishService]).
 */
@Service
class ModelSyncBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate,
    private val modelsRepository: ModelsRepository,
    private val objectMapper: ObjectMapper,
    private val outboxRepository: ModelSyncOutboxRepository,
    @Value("\${arepos.model-sync.outbox-enabled:false}") private val outboxEnabled: Boolean
) {

    @Transactional
    fun broadcastModelChanged(modelId: UUID, source: String, events: List<ModelSyncEntityEvent> = emptyList()) {
        val bumped = modelsRepository.incrementSyncRevision(modelId)
        if (bumped == 0) {
            return
        }
        val model = modelsRepository.findById(modelId).orElse(null) ?: return
        val revision = model.syncRevision
        val eventId = UUID.randomUUID()
        val actor = CurrentUser.getId()

        val payload = linkedMapOf<String, Any?>(
            "v" to 2,
            "type" to "model_changed",
            "eventId" to eventId.toString(),
            "modelId" to modelId.toString(),
            "modelRevision" to revision,
            "source" to source,
            "serverTime" to Instant.now().toString()
        )
        if (actor != null) {
            payload["actorUserId"] = actor.toString()
        }
        if (events.isNotEmpty()) {
            payload["events"] = events.map { e ->
                mapOf(
                    "type" to e.type,
                    "entity" to e.entity,
                    "id" to e.id.toString(),
                    "revision" to revision
                )
            }
        }

        dispatchPayload(model, payload)
    }

    private fun dispatchPayload(model: ru.kavader.arepos.model.Models, payload: Map<String, Any?>) {
        val mid = requireNotNull(model.id)
        if (outboxEnabled) {
            val json = objectMapper.writeValueAsString(payload)
            outboxRepository.save(
                ModelSyncOutbox(
                    model = model,
                    payload = json,
                    createdAt = Instant.now()
                )
            )
        } else {
            messagingTemplate.convertAndSend("/topic/models/$mid", payload)
        }
    }

    fun broadcastDiagramLive(modelId: UUID, diagramId: UUID, instances: JsonNode) {
        val eventId = UUID.randomUUID()
        val payload = linkedMapOf<String, Any?>(
            "v" to 2,
            "type" to "diagram_live",
            "eventId" to eventId.toString(),
            "modelId" to modelId.toString(),
            "diagramId" to diagramId.toString(),
            "serverTime" to Instant.now().toString(),
            "instances" to instances
        )
        val actor = CurrentUser.getId()
        if (actor != null) {
            payload["actorUserId"] = actor.toString()
        }
        messagingTemplate.convertAndSend("/topic/models/$modelId", payload)
    }

    fun broadcastDiagramPointer(
        modelId: UUID,
        diagramId: UUID,
        worldX: Double,
        worldY: Double,
        visible: Boolean
    ) {
        val eventId = UUID.randomUUID()
        val payload = linkedMapOf<String, Any?>(
            "v" to 2,
            "type" to "diagram_pointer",
            "eventId" to eventId.toString(),
            "modelId" to modelId.toString(),
            "diagramId" to diagramId.toString(),
            "worldX" to worldX,
            "worldY" to worldY,
            "visible" to visible,
            "serverTime" to Instant.now().toString()
        )
        val actor = CurrentUser.getId()
        if (actor != null) {
            payload["actorUserId"] = actor.toString()
        }
        messagingTemplate.convertAndSend("/topic/models/$modelId", payload)
    }

    fun broadcastDiagramSpectators(modelId: UUID, diagramId: UUID, viewers: List<DiagramSpectatorView>) {
        val eventId = UUID.randomUUID()
        val payload = linkedMapOf<String, Any?>(
            "v" to 2,
            "type" to "diagram_spectators",
            "eventId" to eventId.toString(),
            "modelId" to modelId.toString(),
            "diagramId" to diagramId.toString(),
            "serverTime" to Instant.now().toString(),
            "viewers" to viewers.map { v ->
                mapOf(
                    "userId" to v.userId.toString(),
                    "displayName" to v.displayName
                )
            }
        )
        messagingTemplate.convertAndSend("/topic/models/$modelId", payload)
    }
}
