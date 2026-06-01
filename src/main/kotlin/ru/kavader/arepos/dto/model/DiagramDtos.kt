package ru.kavader.arepos.dto.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class DiagramRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val version: String,
    val ownerId: UUID? = null,
    @field:NotNull val modelId: UUID,
    val nodeId: UUID? = null,
    val notationId: UUID,
    val attrs: String? = null
)

data class DiagramUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val modelId: UUID? = null,
    val nodeId: UUID? = null,
    val notationId: UUID? = null,
    val attrs: String? = null
)

data class DiagramResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val modelId: UUID,
    val nodeId: UUID?,
    val notationId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class DiagramShareLinkRequest(
    val diagramId: UUID? = null,
    val modelId: UUID? = null,
    val diagramName: String? = null,
    val latest: Boolean? = null
)

data class DiagramShareLinkResponse(
    val url: String,
    val token: UUID
)
