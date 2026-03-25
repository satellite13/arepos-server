package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import ru.kavader.arepos.dto.DiagramSpectatorView
import ru.kavader.arepos.security.CurrentUser
import java.time.Instant
import java.util.UUID

/**
 * Push-уведомление подписчикам модели: пора обновить снимок (дерево / связи / диаграммы).
 * Транспорт: STOMP topic `/topic/models/{modelId}`.
 */
@Service
class ModelSyncBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate
) {

    fun broadcastModelChanged(modelId: UUID, source: String) {
        val actor = CurrentUser.getId()
        val payload = linkedMapOf(
            "v" to 1,
            "type" to "model_changed",
            "modelId" to modelId.toString(),
            "source" to source,
            "serverTime" to Instant.now().toString()
        )
        if (actor != null) {
            payload["actorUserId"] = actor.toString()
        }
        messagingTemplate.convertAndSend("/topic/models/$modelId", payload)
    }

    fun broadcastDiagramLive(modelId: UUID, diagramId: UUID, instances: JsonNode) {
        val payload = linkedMapOf<String, Any?>(
            "v" to 1,
            "type" to "diagram_live",
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
        val payload = linkedMapOf<String, Any?>(
            "v" to 1,
            "type" to "diagram_pointer",
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
        val payload = linkedMapOf<String, Any?>(
            "v" to 1,
            "type" to "diagram_spectators",
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
