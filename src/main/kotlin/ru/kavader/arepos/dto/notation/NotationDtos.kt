package ru.kavader.arepos.dto.notation

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class NotationRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val version: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NotationUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NotationResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val sourceId: UUID? = null
)

data class NotationMetaResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val ownerEmail: String
)
