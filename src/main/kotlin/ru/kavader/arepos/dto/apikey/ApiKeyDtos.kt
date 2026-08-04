package ru.kavader.arepos.dto.apikey

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.*

object ApiKeyScopes {
    const val MODELS_READ = "models:read"
    const val MODELS_WRITE = "models:write"

    val ALL = setOf(MODELS_READ, MODELS_WRITE)
}

data class CreateApiKeyRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,
    @field:NotEmpty
    val scopes: List<String>,
    val modelIds: List<UUID>? = null,
    val expiresAt: Instant? = null
)

data class UpdateApiKeyRequest(
    @field:Size(max = 200)
    val name: String? = null,
    val scopes: List<String>? = null,
    val modelIds: List<UUID>? = null,
    val clearModelIds: Boolean = false,
    val expiresAt: Instant? = null,
    val clearExpiresAt: Boolean = false
)

data class ApiKeyResponse(
    val id: UUID,
    val name: String,
    val tokenPrefix: String,
    val scopes: List<String>,
    val modelIds: List<UUID>?,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
    val lastUsedAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class CreateApiKeyResponse(
    val key: String,
    val apiKey: ApiKeyResponse
)

data class ExchangeApiKeyRequest(
    @field:NotBlank
    @field:Size(max = 256)
    val apiKey: String
)

data class ExchangeApiKeyResponse(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
    val scopes: List<String>,
    val modelIds: List<UUID>?
)
