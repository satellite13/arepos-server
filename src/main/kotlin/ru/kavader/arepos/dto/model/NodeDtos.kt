package ru.kavader.arepos.dto.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class NodeRequest(
    @field:NotBlank val name: String,
    @field:NotNull val modelId: UUID,
    val ownerId: UUID? = null,
    @field:NotNull val nodeTypeId: UUID,
    val parentNodeId: UUID? = null,
    val attrs: String? = null,
    val stableId: UUID? = null
)

data class NodeUpdateRequest(
    val name: String? = null,
    val modelId: UUID? = null,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val parentNodeId: UUID? = null,
    val attrs: String? = null
)

data class NodeResponse(
    val id: UUID,
    val stableId: UUID,
    val name: String,
    val modelId: UUID,
    val ownerId: UUID,
    val nodeTypeId: UUID,
    val parentNodeId: UUID?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
