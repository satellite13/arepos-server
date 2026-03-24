package ru.kavader.arepos.service

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
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
}
