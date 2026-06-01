package ru.kavader.arepos.dto.model

import java.time.Instant
import java.util.UUID

data class LinkRequest(
    val sourceId: UUID,
    val targetId: UUID,
    val modelId: UUID,
    val ownerId: UUID? = null,
    val linkTypeId: UUID,
    val attrs: String? = null,
    val stableId: UUID? = null
)

data class LinkUpdateRequest(
    val sourceId: UUID? = null,
    val targetId: UUID? = null,
    val modelId: UUID? = null,
    val ownerId: UUID? = null,
    val linkTypeId: UUID? = null,
    val attrs: String? = null
)

data class LinkResponse(
    val id: UUID,
    val stableId: UUID,
    val sourceId: UUID,
    val targetId: UUID,
    val modelId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
