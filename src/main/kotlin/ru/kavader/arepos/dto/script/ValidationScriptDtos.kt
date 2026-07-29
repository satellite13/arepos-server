package ru.kavader.arepos.dto.script

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.*

data class ValidationScriptRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    @field:NotBlank val source: String,
    val attrs: String? = null
)

data class ValidationScriptUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val source: String? = null,
    val attrs: String? = null
)

data class ValidationScriptResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val source: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
