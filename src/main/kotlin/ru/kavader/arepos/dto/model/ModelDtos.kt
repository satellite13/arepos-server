package ru.kavader.arepos.dto.model

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class ModelRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val version: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class ModelUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class ModelResponse(
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

data class GroupedEntityResponse<T>(val groups: List<EntityGroupResponse<T>>)
data class EntityGroupResponse<T>(val name: String, val versions: List<T>)
