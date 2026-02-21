package ru.kavader.arepos.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*
import javax.crypto.SecretKey

@ConfigurationProperties(prefix = "arepos.jwt")
data class JwtProperties(
    val secret: String,
    val accessExpiration: Duration = Duration.ofMinutes(30),
    val refreshExpiration: Duration = Duration.ofDays(7)
)

@Component
class JwtTokenProvider(private val jwtProperties: JwtProperties) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    fun generateAccessToken(userId: UUID, role: String): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessExpiration.toMillis())
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .claim("type", "access")
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
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getUserId(token: String): UUID {
        return UUID.fromString(parseClaims(token).subject)
    }

    fun getRole(token: String): String {
        return parseClaims(token)["role"] as String
    }

    fun getTokenType(token: String): String {
        return parseClaims(token)["type"] as String
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
