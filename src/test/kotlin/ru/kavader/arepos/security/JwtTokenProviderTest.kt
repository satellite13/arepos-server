package ru.kavader.arepos.security

import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtTokenProviderTest {
    private val jwtSecret = "test-secret-key-that-is-long-enough-for-hmac-sha-256-algorithm!!"

    private val provider = JwtTokenProvider(
        JwtProperties(
            secret = jwtSecret,
            issuer = "arepos",
            audience = "arepos-api",
            accessExpiration = Duration.ofMinutes(30),
            refreshExpiration = Duration.ofDays(7)
        )
    )

    @Test
    fun `generates and validates access token`() {
        val userId = UUID.randomUUID()
        val token = provider.generateAccessToken(userId, "ADMIN")

        assertTrue(provider.validateToken(token))
        assertEquals(userId, provider.getUserId(token))
        assertEquals("ADMIN", provider.getRole(token))
        assertEquals(TokenType.ACCESS, provider.getTokenType(token))
    }

    @Test
    fun `generates and validates refresh token`() {
        val userId = UUID.randomUUID()
        val token = provider.generateRefreshToken(userId)

        assertTrue(provider.validateToken(token))
        assertEquals(userId, provider.getUserId(token))
        assertEquals(TokenType.REFRESH, provider.getTokenType(token))
    }

    @Test
    fun `returns false for invalid token`() {
        assertFalse(provider.validateToken("invalid.token.here"))
    }

    @Test
    fun `returns false for expired token`() {
        val expiredProvider = JwtTokenProvider(
            JwtProperties(
                secret = jwtSecret,
                issuer = "arepos",
                audience = "arepos-api",
                accessExpiration = Duration.ofMillis(1),
                refreshExpiration = Duration.ofMillis(1)
            )
        )
        val token = expiredProvider.generateAccessToken(UUID.randomUUID(), "USER")
        Thread.sleep(10)
        assertFalse(expiredProvider.validateToken(token))
    }

    @Test
    fun `returns false for token with invalid issuer`() {
        val foreignIssuerProvider = JwtTokenProvider(
            JwtProperties(
                secret = jwtSecret,
                issuer = "other-service",
                audience = "arepos-api",
                accessExpiration = Duration.ofMinutes(30),
                refreshExpiration = Duration.ofDays(7)
            )
        )
        val token = foreignIssuerProvider.generateAccessToken(UUID.randomUUID(), "USER")

        assertFalse(provider.validateToken(token))
    }

    @Test
    fun `returns false for token with invalid audience`() {
        val foreignAudienceProvider = JwtTokenProvider(
            JwtProperties(
                secret = jwtSecret,
                issuer = "arepos",
                audience = "other-audience",
                accessExpiration = Duration.ofMinutes(30),
                refreshExpiration = Duration.ofDays(7)
            )
        )
        val token = foreignAudienceProvider.generateAccessToken(UUID.randomUUID(), "USER")

        assertFalse(provider.validateToken(token))
    }
}
