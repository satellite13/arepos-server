package ru.kavader.arepos.dto.notation

import java.time.Instant
import java.util.UUID

data class NodeShapeRequest(
    val name: String,
    val outline: String? = null,
    val contentArea: String? = null,
    val attrs: String? = null
)

data class NodeShapeUpdateRequest(
    val name: String? = null,
    val outline: String? = null,
    val contentArea: String? = null,
    val attrs: String? = null
)

data class NodeShapeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val outline: String?,
    val contentArea: String?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val canEdit: Boolean
)
