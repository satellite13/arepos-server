package ru.kavader.arepos.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.SecretKey

@ConfigurationProperties(prefix = "arepos.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "arepos",
    val audience: String = "arepos-api",
    val accessExpiration: Duration = Duration.ofMinutes(30),
    val refreshExpiration: Duration = Duration.ofDays(7)
)

@Component
class JwtTokenProvider(private val jwtProperties: JwtProperties) {
    companion object {
        private val log = LoggerFactory.getLogger(JwtTokenProvider::class.java)
        const val INSECURE_DEFAULT_SECRET =
            "default-dev-secret-key-change-in-production-must-be-at-least-256-bits-long!!"
        private const val MIN_SECRET_BYTES = 32
        private const val INVALID_TOKEN_LOG_SAMPLE_EVERY = 100L
    }

    private val invalidTokenLogCounter = AtomicLong(0)

    init {
        require(jwtProperties.secret.isNotBlank()) {
            "JWT secret must not be blank"
        }
        require(jwtProperties.secret != INSECURE_DEFAULT_SECRET) {
            "JWT secret must be provided via JWT_SECRET and must not use insecure default"
        }
        require(jwtProperties.secret.toByteArray(Charsets.UTF_8).size >= MIN_SECRET_BYTES) {
            "JWT secret must be at least $MIN_SECRET_BYTES bytes"
        }
    }

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccessToken(userId: UUID, role: String): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessExpiration.toMillis())
        return Jwts.builder()
            .subject(userId.toString())
            .issuer(jwtProperties.issuer)
            .audience().add(jwtProperties.audience).and()
            .claim("role", role)
            .claim("type", TokenType.ACCESS.claimValue)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(userId: UUID): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.refreshExpiration.toMillis())
        return Jwts.builder()
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .issuer(jwtProperties.issuer)
            .audience().add(jwtProperties.audience).and()
            .claim("type", TokenType.REFRESH.claimValue)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (ex: Exception) {
            logInvalidToken(ex)
            false
        }
    }

    fun getUserId(token: String): UUID {
        return UUID.fromString(parseClaims(token).subject)
    }

    fun getRole(token: String): String {
        return parseClaims(token)["role"] as String
    }

    fun getTokenType(token: String): TokenType {
        return TokenType.fromClaimValue(parseClaims(token)["type"] as String)
    }

    fun getExpirationInstant(token: String): Instant {
        return parseClaims(token).expiration.toInstant()
    }

    fun accessExpirationSeconds(): Long = jwtProperties.accessExpiration.seconds

    fun refreshExpirationSeconds(): Long = jwtProperties.refreshExpiration.seconds

    private fun parseClaims(token: String): Claims {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        if (claims.issuer != jwtProperties.issuer) {
            throw IllegalArgumentException("Invalid token issuer")
        }
        if (!hasExpectedAudience(claims)) {
            throw IllegalArgumentException("Invalid token audience")
        }
        return claims
    }

    private fun hasExpectedAudience(claims: Claims): Boolean {
        val audienceClaim = claims["aud"] ?: return false
        return when (audienceClaim) {
            is String -> audienceClaim == jwtProperties.audience
            is Collection<*> -> audienceClaim.any { it?.toString() == jwtProperties.audience }
            else -> false
        }
    }

    private fun logInvalidToken(ex: Exception) {
        val attempt = invalidTokenLogCounter.incrementAndGet()
        if ((attempt - 1) % INVALID_TOKEN_LOG_SAMPLE_EVERY != 0L) {
            return
        }
        log.warn(
            "JWT validation failed: type={}, message={}",
            ex::class.simpleName ?: "UnknownException",
            ex.message ?: ""
        )
    }
}
