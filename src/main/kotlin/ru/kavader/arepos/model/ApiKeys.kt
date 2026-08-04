package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@Table(
    name = "api_keys",
    schema = "public",
    indexes = [
        Index(name = "api_keys_owner_idx", columnList = "owner"),
        Index(name = "api_keys_token_hash_idx", columnList = "token_hash")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class ApiKeys(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    var owner: Users,

    @Column(name = "name", nullable = false, length = 200)
    var name: String,

    @Column(name = "token_prefix", nullable = false, length = 32)
    var tokenPrefix: String,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes", columnDefinition = "jsonb", nullable = false)
    var scopes: MutableList<String> = mutableListOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_ids", columnDefinition = "jsonb")
    var modelIds: MutableList<String>? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
