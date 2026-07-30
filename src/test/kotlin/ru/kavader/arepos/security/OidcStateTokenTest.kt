package ru.kavader.arepos.security

import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OidcStateTokenTest {

    private val stateToken = OidcStateToken()

    @Test
    fun `generates and validates state token`() {
        val userId = UUID.randomUUID()
        val token = stateToken.generateStateToken(userId)

        assertTrue(token.contains("|"))
        assertTrue(token.contains("."))

        val validatedId = stateToken.validateStateToken(token)
        assertNotNull(validatedId)
        assertEquals(userId, validatedId)
    }

    @Test
    fun `returns null for invalid format`() {
        assertNull(stateToken.validateStateToken("invalid"))
        assertNull(stateToken.validateStateToken("a.b.c"))
        assertNull(stateToken.validateStateToken(""))
    }

    @Test
    fun `returns null for wrong signature`() {
        val userId = UUID.randomUUID()
        val token = stateToken.generateStateToken(userId)
        val tampered = token.substring(0, token.length - 4) + "0000"

        assertNull(stateToken.validateStateToken(tampered))
    }

    @Test
    fun `different instances cannot validate each other's tokens`() {
        val userId = UUID.randomUUID()
        val token = stateToken.generateStateToken(userId)
        val other = OidcStateToken()

        assertNull(other.validateStateToken(token))
    }
}
