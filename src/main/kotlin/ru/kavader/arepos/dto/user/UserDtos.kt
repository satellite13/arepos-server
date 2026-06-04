package ru.kavader.arepos.dto.user

import ru.kavader.arepos.model.Role
import java.time.Instant
import java.util.*

data class UserRequest(
    val email: String,
    val attrs: String? = null,
    val role: Role? = null
)

data class UserUpdateRequest(
    val email: String? = null,
    val attrs: String? = null,
    val role: Role? = null,
    val isActive: Boolean? = null,
    val password: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val position: String? = null
)

data class UserProfileUpdateRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val position: String? = null
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val role: String,
    val isActive: Boolean,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val position: String?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class UserPublicResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val position: String?
)

data class BatchUserPublicRequest(val ids: List<UUID>)
