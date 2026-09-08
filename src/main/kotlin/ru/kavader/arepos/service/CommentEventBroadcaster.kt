package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import ru.kavader.arepos.model.DiagramComment
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.security.CurrentUser
import java.time.Instant
import java.util.UUID

/**
 * Публикация событий комментариев подписчикам модели.
 * Реиспользует существующий STOMP-топик `/topic/models/{modelId}` с типом события
 * `diagram_comment` — авторизация подписок уже решена перехватчиком модели.
 */
@Service
class CommentEventBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper
) {
    fun broadcastCommentEvent(
        diagram: Diagrams,
        action: String,
        comment: DiagramComment?
    ) {
        val modelId = diagram.model.id ?: return
        val diagramId = diagram.id ?: return
        val payload = linkedMapOf<String, Any?>(
            "v" to 2,
            "type" to "diagram_comment",
            "eventId" to UUID.randomUUID().toString(),
            "modelId" to modelId.toString(),
            "diagramId" to diagramId.toString(),
            "action" to action,
            "serverTime" to Instant.now().toString()
        )
        comment?.id?.let { payload["commentId"] = it.toString() }
        val threadId = comment?.thread?.id ?: comment?.id
        threadId?.let { payload["threadId"] = it.toString() }
        comment?.instanceId?.let { payload["instanceId"] = it }
        CurrentUser.getId()?.let { payload["actorUserId"] = it.toString() }
        try {
            messagingTemplate.convertAndSend("/topic/models/$modelId", payload)
        } catch (e: Exception) {
            // Транспорт не должен ломать основную операцию
        }
    }
}
