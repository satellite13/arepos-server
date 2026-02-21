package ru.kavader.arepos.security

import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtTokenProviderTest {

    private val provider = JwtTokenProvider(
        JwtProperties(
            secret = "test-secret-key-that-is-long-enough-for-hmac-sha-256-algorithm!!",
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
        assertEquals("access", provider.getTokenType(token))
    }

    @Test
    fun `generates and validates refresh token`() {
        val userId = UUID.randomUUID()
        val token = provider.generateRefreshToken(userId)

        assertTrue(provider.validateToken(token))
        assertEquals(userId, provider.getUserId(token))
        assertEquals("refresh", provider.getTokenType(token))
    }

    @Test
    fun `returns false for invalid token`() {
        assertFalse(provider.validateToken("invalid.token.here"))
    }

    @Test
    fun `returns false for expired token`() {
        val expiredProvider = JwtTokenProvider(
            JwtProperties(
                secret = "test-secret-key-that-is-long-enough-for-hmac-sha-256-algorithm!!",
                accessExpiration = Duration.ofMillis(1),
                refreshExpiration = Duration.ofMillis(1)
            )
        )
        val token = expiredProvider.generateAccessToken(UUID.randomUUID(), "USER")
        Thread.sleep(10)
        assertFalse(expiredProvider.validateToken(token))
    }
}
