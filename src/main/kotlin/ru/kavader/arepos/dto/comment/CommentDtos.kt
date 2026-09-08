package ru.kavader.arepos.dto.comment

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

data class CommentAuthorResponse(
    val id: UUID,
    val displayName: String
)

data class CommentAttachmentResponse(
    val fileId: UUID,
    val filename: String,
    val contentType: String,
    val size: Long,
    val url: String
)

data class CommentReactionResponse(
    val emoji: String,
    val count: Long,
    val viewerReacted: Boolean
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CommentMessageResponse(
    val id: UUID,
    val author: CommentAuthorResponse,
    val bodyMd: String,
    val mentions: List<UUID>,
    val editedAt: Instant?,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val attachments: List<CommentAttachmentResponse>,
    val reactions: List<CommentReactionResponse>,
    val linkPreview: CommentLinkPreviewResponse?
)

data class CommentThreadResponse(
    val id: UUID,
    val targetType: String,
    val instanceId: String?,
    val elementName: String?,
    val elementExists: Boolean,
    val isResolved: Boolean,
    val resolvedAt: Instant?,
    val resolvedReason: String?,
    val lastActivityAt: Instant,
    val replyCount: Int,
    val message: CommentMessageResponse,
    val replies: List<CommentMessageResponse>
)

data class CommentThreadsResponse(
    val threads: List<CommentThreadResponse>,
    val totalCount: Long
)

data class CommentCreateRequest(
    val bodyMd: String,
    val targetType: String = "diagram",
    val instanceId: String? = null,
    val elementName: String? = null,
    val threadId: UUID? = null,
    val attachmentFileIds: List<UUID> = emptyList()
)

data class CommentUpdateRequest(
    val bodyMd: String? = null,
    val attachmentFileIds: List<UUID>? = null
)

data class CommentReadStateRequest(
    val lastReadAt: Instant
)

data class CommentReadStateResponse(
    val lastReadAt: Instant,
    val unreadCount: Long
)

data class CommentCountResponse(
    val diagramId: UUID,
    val totalCount: Long,
    val unreadCount: Long,
    val byInstance: Map<String, Long>,
    val byInstanceUnresolved: Map<String, Long> = emptyMap()
)

data class CommentLinkPreviewResponse(
    val url: String,
    val title: String?,
    val description: String?,
    val siteName: String?,
    val imageUrl: String?
)

data class LinkPreviewResolveRequest(
    val url: String
)

data class NotificationResponse(
    val id: UUID,
    val type: String,
    val payload: Map<String, Any?>,
    val readAt: Instant?,
    val createdAt: Instant
)
