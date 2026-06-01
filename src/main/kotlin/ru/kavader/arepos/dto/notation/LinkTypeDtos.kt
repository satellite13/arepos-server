package ru.kavader.arepos.dto.notation

import java.time.Instant
import java.util.UUID

data class LinkTypeRequest(
    val name: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class LinkTypeUpdateRequest(
    val name: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class LinkTypeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
