package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(
    name = "refresh_tokens",
    schema = "public",
    indexes = [
        Index(name = "refresh_tokens_user_id_idx", columnList = "user_id"),
        Index(name = "refresh_tokens_expires_at_idx", columnList = "expires_at")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class RefreshTokens(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: Users,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
)
