package ru.kavader.arepos.dto.notation

import java.time.Instant
import java.util.UUID

data class NodeTypeRequest(
    val name: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NodeTypeUpdateRequest(
    val name: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NodeTypeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
