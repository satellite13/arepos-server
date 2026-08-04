package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.apikey.*
import ru.kavader.arepos.model.ApiKeys
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ApiKeysRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.JwtTokenProvider
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*

@Service
class ApiKeyService(
    private val apiKeysRepository: ApiKeysRepository,
    private val usersRepository: UsersRepository,
    private val jwtTokenProvider: JwtTokenProvider
) {
    companion object {
        const val KEY_PREFIX = "warchi_ak_"
        private const val RANDOM_BYTES = 32
        private val secureRandom = SecureRandom()
    }

    fun listMine(): List<ApiKeyResponse> {
        val user = currentUser()
        return apiKeysRepository.findByOwnerOrderByCreatedAtDesc(user).map { it.toResponse() }
    }

    @Transactional
    fun create(request: CreateApiKeyRequest): CreateApiKeyResponse {
        val user = currentUser()
        val scopes = normalizeScopes(request.scopes)
        val modelIds = normalizeModelIds(request.modelIds)
        if (request.expiresAt != null && !request.expiresAt.isAfter(Instant.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future")
        }

        val plaintext = generatePlaintext()
        val now = Instant.now()
        val entity = apiKeysRepository.save(
            ApiKeys(
                owner = user,
                name = request.name.trim(),
                tokenPrefix = plaintext.removePrefix(KEY_PREFIX).take(8),
                tokenHash = hashToken(plaintext),
                scopes = scopes.toMutableList(),
                modelIds = modelIds?.map { it.toString() }?.toMutableList(),
                expiresAt = request.expiresAt,
                createdAt = now,
                updatedAt = now
            )
        )
        return CreateApiKeyResponse(key = plaintext, apiKey = entity.toResponse())
    }

    @Transactional
    fun update(id: UUID, request: UpdateApiKeyRequest): ApiKeyResponse {
        val user = currentUser()
        val entity = apiKeysRepository.findByIdAndOwner(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "API key $id not found")
        if (entity.revokedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update a revoked API key")
        }

        request.name?.trim()?.takeIf { it.isNotEmpty() }?.let { entity.name = it }
        request.scopes?.let { entity.scopes = normalizeScopes(it).toMutableList() }
        when {
            request.clearModelIds -> entity.modelIds = null
            request.modelIds != null -> entity.modelIds = normalizeModelIds(request.modelIds)
                ?.map { it.toString() }
                ?.toMutableList()
        }
        when {
            request.clearExpiresAt -> entity.expiresAt = null
            request.expiresAt != null -> {
                if (!request.expiresAt.isAfter(Instant.now())) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future")
                }
                entity.expiresAt = request.expiresAt
            }
        }
        entity.updatedAt = Instant.now()
        return apiKeysRepository.save(entity).toResponse()
    }

    @Transactional
    fun revoke(id: UUID) {
        val user = currentUser()
        val entity = apiKeysRepository.findByIdAndOwner(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "API key $id not found")
        if (entity.revokedAt == null) {
            val now = Instant.now()
            entity.revokedAt = now
            entity.updatedAt = now
            apiKeysRepository.save(entity)
        }
    }

    @Transactional
    fun exchange(request: ExchangeApiKeyRequest): ExchangeApiKeyResponse {
        val plaintext = request.apiKey.trim()
        if (!plaintext.startsWith(KEY_PREFIX) || plaintext.length < KEY_PREFIX.length + 16) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key")
        }

        val entity = apiKeysRepository.findByTokenHash(hashToken(plaintext))
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key")

        val now = Instant.now()
        if (entity.revokedAt != null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key has been revoked")
        }
        if (entity.expiresAt != null && !entity.expiresAt!!.isAfter(now)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key has expired")
        }
        val owner = entity.owner
        if (!owner.isActive) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is inactive")
        }

        entity.lastUsedAt = now
        entity.updatedAt = now
        apiKeysRepository.save(entity)

        val scopes = entity.scopes.toSet()
        val modelIds = entity.modelIds?.mapNotNull {
            runCatching { UUID.fromString(it) }.getOrNull()
        }?.toSet()

        val accessToken = jwtTokenProvider.generateMcpAccessToken(
            userId = owner.id!!,
            role = owner.role.name,
            scopes = scopes,
            modelIds = modelIds
        )

        return ExchangeApiKeyResponse(
            accessToken = accessToken,
            expiresIn = jwtTokenProvider.mcpAccessExpirationSeconds(),
            scopes = scopes.toList().sorted(),
            modelIds = modelIds?.toList()?.sortedBy { it.toString() }
        )
    }

    private fun currentUser(): Users {
        val userId = CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")
        return usersRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")
        }
    }

    private fun normalizeScopes(scopes: List<String>): List<String> {
        val normalized = scopes.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (normalized.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one scope is required")
        }
        val unknown = normalized - ApiKeyScopes.ALL
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unknown scopes: ${unknown.sorted().joinToString(", ")}"
            )
        }
        // write implies read for practical use, but we store exactly what was requested
        // and enforce write/read separately; if only write is given, also grant read.
        val effective = normalized.toMutableSet()
        if (ApiKeyScopes.MODELS_WRITE in effective) {
            effective += ApiKeyScopes.MODELS_READ
        }
        return effective.sorted()
    }

    private fun normalizeModelIds(modelIds: List<UUID>?): List<UUID>? {
        if (modelIds == null) return null
        val distinct = modelIds.distinct()
        if (distinct.isEmpty()) return null
        return distinct
    }

    private fun generatePlaintext(): String {
        val bytes = ByteArray(RANDOM_BYTES)
        secureRandom.nextBytes(bytes)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return KEY_PREFIX + encoded
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun ApiKeys.toResponse(): ApiKeyResponse = ApiKeyResponse(
        id = id!!,
        name = name,
        tokenPrefix = tokenPrefix,
        scopes = scopes.toList(),
        modelIds = modelIds?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() },
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        lastUsedAt = lastUsedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
