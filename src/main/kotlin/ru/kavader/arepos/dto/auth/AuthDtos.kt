package ru.kavader.arepos.dto.auth

import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val position: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class AdminRegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val position: String? = null,
    val adminSecret: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserInfoResponse
)

data class UserInfoResponse(
    val id: UUID,
    val email: String,
    val role: String,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val position: String?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
