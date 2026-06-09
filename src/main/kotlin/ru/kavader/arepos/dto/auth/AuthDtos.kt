package ru.kavader.arepos.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String,
    @field:NotBlank
    @field:Size(max = 100)
    val firstName: String,
    @field:NotBlank
    @field:Size(max = 100)
    val lastName: String,
    @field:Size(max = 100)
    val middleName: String? = null,
    @field:Size(max = 100)
    val position: String? = null
)

data class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String
)

data class RefreshRequest(
    @field:Size(max = 4096)
    val refreshToken: String? = null
)

data class AdminRegisterRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String,
    @field:NotBlank
    @field:Size(max = 100)
    val firstName: String,
    @field:NotBlank
    @field:Size(max = 100)
    val lastName: String,
    @field:Size(max = 100)
    val middleName: String? = null,
    @field:Size(max = 100)
    val position: String? = null,
    @field:NotBlank
    @field:Size(max = 256)
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
