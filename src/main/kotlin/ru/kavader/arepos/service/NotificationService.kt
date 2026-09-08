package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.dto.comment.NotificationResponse
import ru.kavader.arepos.model.Notification
import ru.kavader.arepos.repository.NotificationRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun createNotification(recipientId: UUID, type: String, payload: Map<String, Any?>) {
        val recipient = usersRepository.findById(recipientId).orElse(null) ?: return
        val notification = Notification(
            user = recipient,
            type = type,
            payload = objectMapper.writeValueAsString(payload),
            createdAt = Instant.now()
        )
        notificationRepository.save(notification)
        pushToUser(recipientId, notification)
    }

    private fun pushToUser(recipientId: UUID, notification: Notification) {
        try {
            val payload: Map<String, Any?> =
                objectMapper.readValue(notification.payload ?: "{}", objectMapper.typeFactory.constructMapType(
                    Map::class.java, String::class.java, Any::class.java
                ))
            messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                "/queue/notifications",
                payload
            )
        } catch (e: Exception) {
            log.debug("Failed to push notification over WebSocket: {}", e.message)
        }
    }

    @Transactional(readOnly = true)
    fun list(pageable: Pageable): Page<NotificationResponse> {
        val userId = accessService.currentUserId()
        val safePageable = PageRequest.of(
            pageable.pageNumber.coerceAtLeast(0),
            pageable.pageSize.coerceIn(1, 100),
            if (pageable.sort.isUnsorted) Sort.by(Sort.Direction.DESC, "createdAt") else pageable.sort
        )
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, safePageable)
            .map(::toResponse)
    }

    @Transactional(readOnly = true)
    fun unreadCount(): Long = notificationRepository.countByUserIdAndReadAtIsNull(accessService.currentUserId())

    @Transactional
    fun markAllRead(): Int = notificationRepository.markAllReadByUserId(accessService.currentUserId())

    private fun toResponse(notification: Notification): NotificationResponse {
        @Suppress("UNCHECKED_CAST")
        val payload = runCatching {
            objectMapper.readValue(notification.payload ?: "{}", Map::class.java) as Map<String, Any?>
        }.getOrDefault(emptyMap())
        return NotificationResponse(
            id = notification.id!!,
            type = notification.type,
            payload = payload,
            readAt = notification.readAt,
            createdAt = notification.createdAt ?: Instant.now()
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(NotificationService::class.java)
    }
}
