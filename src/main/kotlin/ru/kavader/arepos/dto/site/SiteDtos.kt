package ru.kavader.arepos.dto.site

import com.fasterxml.jackson.databind.JsonNode
import ru.kavader.arepos.dto.system.AuditLogResponse
import java.time.Instant
import java.util.UUID

data class FeedbackAuthorResponse(
    val id: UUID,
    val displayName: String
)

data class FeedbackItemResponse(
    val id: UUID,
    val type: String,
    val title: String,
    val body: String,
    val status: String,
    val author: FeedbackAuthorResponse,
    val voteCount: Int,
    val votedByMe: Boolean = false,
    val comments: List<FeedbackCommentResponse> = emptyList(),
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val mergedIntoId: UUID? = null,
    val mergedAt: Instant? = null,
    val audit: List<AuditLogResponse> = emptyList()
)

data class FeedbackCommentResponse(
    val id: UUID,
    val body: String,
    val author: FeedbackAuthorResponse,
    val createdAt: Instant?
)

data class CreateFeedbackRequest(
    val type: String,
    val title: String,
    val body: String
)

data class UpdateFeedbackRequest(
    val title: String? = null,
    val body: String? = null,
    val status: String? = null,
    val type: String? = null,
    val baseUpdatedAt: Instant? = null
)

data class MergeFeedbackRequest(
    val targetId: UUID
)

data class MergeFeedbackResponse(
    val sourceId: UUID,
    val targetId: UUID,
    val mergedAt: Instant,
    val target: FeedbackItemResponse
)

data class CreateFeedbackCommentRequest(
    val body: String
)

data class RoadmapLinkedItemResponse(
    val id: UUID,
    val type: String,
    val title: String,
    val status: String,
    val voteCount: Int
)

data class RoadmapMilestoneResponse(
    val id: UUID,
    val title: String,
    val description: String,
    val status: String,
    val sortOrder: Int,
    val targetPeriod: String?,
    val items: List<RoadmapLinkedItemResponse> = emptyList(),
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val audit: List<AuditLogResponse> = emptyList()
)

data class CreateRoadmapMilestoneRequest(
    val title: String,
    val description: String = "",
    val status: String = "planned",
    val sortOrder: Int = 0,
    val targetPeriod: String? = null
)

data class UpdateRoadmapMilestoneRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val sortOrder: Int? = null,
    val targetPeriod: JsonNode? = null,
    val baseUpdatedAt: Instant? = null
)

data class SetRoadmapMilestoneItemsRequest(
    val feedbackItemIds: List<UUID>
)

data class ReorderRoadmapMilestonesRequest(
    val items: List<ReorderRoadmapMilestoneItemRequest>
)

data class ReorderRoadmapMilestoneItemRequest(
    val id: UUID,
    val sortOrder: Int,
    val baseUpdatedAt: Instant?
)

data class RoadmapConflictItem(
    val id: UUID,
    val serverUpdatedAt: Instant?
)

class RoadmapConflictException(
    val error: String,
    val conflicts: List<RoadmapConflictItem>
) : RuntimeException("Roadmap conflict: ${conflicts.size} milestone(s)") {
    init {
        require(conflicts.isNotEmpty()) { "RoadmapConflictItem list must not be empty" }
    }
}

data class TutorialVideoResponse(
    val id: UUID,
    val title: String,
    val description: String,
    val provider: String,
    val externalId: String,
    val embedUrl: String,
    val thumbnailUrl: String?,
    val sortOrder: Int,
    val published: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class CreateTutorialRequest(
    val title: String,
    val description: String = "",
    val provider: String,
    val externalId: String,
    val embedUrl: String? = null,
    val thumbnailUrl: String? = null,
    val sortOrder: Int = 0,
    val published: Boolean = true
)

data class UpdateTutorialRequest(
    val title: String? = null,
    val description: String? = null,
    val provider: String? = null,
    val externalId: String? = null,
    val embedUrl: String? = null,
    val thumbnailUrl: String? = null,
    val sortOrder: Int? = null,
    val published: Boolean? = null
)

data class DownloadAssetResponse(
    val id: UUID,
    val title: String,
    val description: String,
    val kind: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val versionLabel: String?,
    val sortOrder: Int,
    val published: Boolean,
    val downloadCount: Long,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

