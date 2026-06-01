package ru.kavader.arepos.dto.notation

import java.time.Instant
import java.util.UUID

data class ComponentRequest(
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID,
    val attrs: String? = null
)

data class ComponentUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val notationId: UUID? = null,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val attrs: String? = null
)

data class ComponentResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID,
    val nodeTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
