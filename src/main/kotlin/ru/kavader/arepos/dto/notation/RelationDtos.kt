package ru.kavader.arepos.dto.notation

import java.time.Instant
import java.util.UUID

data class RelationRequest(
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID? = null,
    val linkTypeId: UUID,
    val attrs: String? = null
)

data class RelationUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val notationId: UUID? = null,
    val ownerId: UUID? = null,
    val linkTypeId: UUID? = null,
    val attrs: String? = null
)

data class RelationResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
