package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.comment.NotificationResponse
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.service.NotificationService

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notifications (comments and mentions)")
class NotificationsController(
    private val notificationService: NotificationService
) {
    @GetMapping
    @Operation(summary = "List current user notifications")
    fun list(pageable: Pageable): ListResponse<NotificationResponse> =
        notificationService.list(pageable).content.toListResponse()

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count")
    fun unreadCount(): Map<String, Long> = mapOf("unreadCount" to notificationService.unreadCount())

    @PostMapping("/read")
    @Operation(summary = "Mark all notifications as read")
    fun markAllRead(): Map<String, Int> = mapOf("updated" to notificationService.markAllRead())
}
