package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.apikey.*
import ru.kavader.arepos.model.ApiKeys
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ApiKeysRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ACCESS_DENIED
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.JwtTokenProvider
import ru.kavader.arepos.security.ResourceAccessService
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*

@Service
class ApiKeyService(
    private val apiKeysRepository: ApiKeysRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val jwtTokenProvider: JwtTokenProvider
) {
    companion object {
        const val KEY_PREFIX = "warchi_ak_"
        const val MAX_GRANTS = 50
        private const val RANDOM_BYTES = 32
        private val secureRandom = SecureRandom()
    }

    private data class NormalizedCreate(
        val mode: String,
        val scopes: List<String>?,
        val grants: List<ApiKeyGrantDto>?
    )

    fun listMine(): List<ApiKeyResponse> {
        val user = currentUser()
        return apiKeysRepository.findByOwnerOrderByCreatedAtDesc(user).map { it.toResponse() }
    }

    fun listForUser(userId: UUID): List<ApiKeyResponse> {
        requireAdminPanel()
        val owner = usersRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $userId not found")
        }
        return apiKeysRepository.findByOwnerOrderByCreatedAtDesc(owner).map { it.toResponse() }
    }

    @Transactional
    fun create(request: CreateApiKeyRequest): CreateApiKeyResponse {
        val user = currentUser()
        val normalized = normalizeCreate(request)
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
                mode = normalized.mode,
                scopes = normalized.scopes?.toMutableList(),
                grants = serializeGrants(normalized.grants),
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
    fun revokeForUser(userId: UUID, keyId: UUID) {
        requireAdminPanel()
        val owner = usersRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $userId not found")
        }
        val entity = apiKeysRepository.findByIdAndOwner(keyId, owner)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "API key $keyId not found")
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

        val mode = entity.mode
        val scopes = entity.scopes?.toSet()
        val grants = resolveGrantsForMode(
            mode = mode,
            raw = entity.grants,
            status = HttpStatus.UNAUTHORIZED
        )

        entity.lastUsedAt = now
        entity.updatedAt = now
        apiKeysRepository.save(entity)

        val accessToken = jwtTokenProvider.generateMcpAccessToken(
            userId = owner.id!!,
            role = owner.role.name,
            mode = mode,
            scopes = scopes,
            grants = grants
        )

        return ExchangeApiKeyResponse(
            accessToken = accessToken,
            expiresIn = jwtTokenProvider.mcpAccessExpirationSeconds(),
            mode = mode,
            scopes = scopes?.toList()?.sorted(),
            grants = grants
        )
    }

    private fun currentUser(): Users {
        val userId = CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")
        return usersRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")
        }
    }

    private fun requireAdminPanel() {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
        }
    }

    private fun normalizeCreate(request: CreateApiKeyRequest): NormalizedCreate {
        val mode = request.mode.trim().lowercase()
        if (mode !in ApiKeyModes.ALL_VALUES) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "mode must be one of: ${ApiKeyModes.ALL_VALUES.sorted().joinToString(", ")}"
            )
        }
        return when (mode) {
            ApiKeyModes.ALL -> {
                if (request.grants != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "grants must be null when mode=all")
                }
                val scopes = request.scopes
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "scopes are required when mode=all")
                NormalizedCreate(
                    mode = ApiKeyModes.ALL,
                    scopes = normalizeScopes(scopes),
                    grants = null
                )
            }

            ApiKeyModes.GRANTS -> {
                if (request.scopes != null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "scopes must be null when mode=grants")
                }
                val grants = request.grants
                if (grants.isNullOrEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "grants are required when mode=grants")
                }
                if (grants.size > MAX_GRANTS) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "At most $MAX_GRANTS grants are allowed"
                    )
                }
                val modelIds = grants.map { it.modelId }
                if (modelIds.size != modelIds.toSet().size) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "grants must have distinct modelIds")
                }
                val normalizedGrants = grants.map { grant ->
                    ApiKeyGrantDto(
                        modelId = grant.modelId,
                        scopes = normalizeScopes(grant.scopes)
                    )
                }.sortedBy { it.modelId.toString() }
                for (grant in normalizedGrants) {
                    val model = modelsRepository.findById(grant.modelId).orElse(null)
                        ?: throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Model ${grant.modelId} not found or not accessible"
                        )
                    if (!accessService.canViewModel(model)) {
                        throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Model ${grant.modelId} not found or not accessible"
                        )
                    }
                }
                NormalizedCreate(
                    mode = ApiKeyModes.GRANTS,
                    scopes = null,
                    grants = normalizedGrants
                )
            }

            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported mode: $mode")
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
        val effective = normalized.toMutableSet()
        if (ApiKeyScopes.MODELS_WRITE in effective) {
            effective += ApiKeyScopes.MODELS_READ
        }
        return effective.sorted()
    }

    private fun serializeGrants(grants: List<ApiKeyGrantDto>?): MutableList<MutableMap<String, Any>>? {
        if (grants == null) return null
        return grants.map { grant ->
            mutableMapOf<String, Any>(
                "modelId" to grant.modelId.toString(),
                "scopes" to grant.scopes.toList()
            )
        }.toMutableList()
    }

    private fun deserializeGrants(raw: MutableList<MutableMap<String, Any>>?): List<ApiKeyGrantDto>? {
        if (raw == null) return null
        return raw.mapNotNull { map ->
            val modelId = runCatching { UUID.fromString(map["modelId"]?.toString()) }.getOrNull()
                ?: return@mapNotNull null
            val scopesRaw = map["scopes"]
            val scopes = when (scopesRaw) {
                is Collection<*> -> scopesRaw.mapNotNull { it?.toString() }
                else -> emptyList()
            }
            ApiKeyGrantDto(modelId = modelId, scopes = scopes)
        }
    }

    private fun resolveGrantsForMode(
        mode: String,
        raw: MutableList<MutableMap<String, Any>>?,
        status: HttpStatus
    ): List<ApiKeyGrantDto>? {
        val grants = deserializeGrants(raw)
        if (mode == ApiKeyModes.GRANTS && grants.isNullOrEmpty()) {
            throw ResponseStatusException(status, "Invalid API key configuration")
        }
        return grants
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
        mode = mode,
        scopes = scopes?.toList(),
        grants = resolveGrantsForMode(
            mode = mode,
            raw = grants,
            status = HttpStatus.BAD_REQUEST
        ),
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        lastUsedAt = lastUsedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
