package ru.kavader.arepos.security

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OidcStateTokenTest {

    private val jwtProperties = JwtProperties(
        secret = "unit-test-jwt-secret-key-at-least-32-bytes-long!!"
    )
    private val stateToken = OidcStateToken(
        OidcProperties(stateSecret = "oidc-state-secret-for-unit-tests"),
        jwtProperties
    )

    @Test
    fun `generates and validates login state`() {
        val issued = stateToken.generateLoginState()
        assertTrue(issued.state.contains("|"))
        assertTrue(issued.state.contains("."))
        assertTrue(issued.codeChallenge.isNotBlank())
        assertTrue(issued.nonce.isNotBlank())

        val validated = stateToken.validateLoginState(issued.state)
        assertNotNull(validated)
        assertEquals(OidcStatePurpose.LOGIN, validated.purpose)
        assertEquals(issued.nonce, validated.nonce)
        assertTrue(validated.codeVerifier.isNotBlank())
        assertNull(stateToken.validateLinkState(issued.state))
    }

    @Test
    fun `generates and validates link state`() {
        val userId = UUID.randomUUID()
        val issued = stateToken.generateLinkState(userId)
        val validated = stateToken.validateLinkState(issued.state)
        assertNotNull(validated)
        assertEquals(userId, validated.subjectId)
        assertEquals(OidcStatePurpose.LINK, validated.purpose)
        assertNull(stateToken.validateLoginState(issued.state))
    }

    @Test
    fun `legacy helpers round-trip subject id`() {
        val userId = UUID.randomUUID()
        val token = stateToken.generateStateToken(userId)
        assertEquals(userId, stateToken.validateStateToken(token))
    }

    @Test
    fun `returns null for invalid format`() {
        assertNull(stateToken.validateLoginState("invalid"))
        assertNull(stateToken.validateLoginState("a.b.c"))
        assertNull(stateToken.validateLoginState(""))
    }

    @Test
    fun `returns null for wrong signature`() {
        val issued = stateToken.generateLoginState()
        val tampered = issued.state.substring(0, issued.state.length - 4) + "0000"
        assertNull(stateToken.validateLoginState(tampered))
    }

    @Test
    fun `different secrets cannot validate each other's tokens`() {
        val issued = stateToken.generateLoginState()
        val other = OidcStateToken(
            OidcProperties(stateSecret = "another-oidc-state-secret-value"),
            jwtProperties
        )
        assertNull(other.validateLoginState(issued.state))
    }

    @Test
    fun `falls back to jwt secret when state secret blank`() {
        val a = OidcStateToken(OidcProperties(stateSecret = ""), jwtProperties)
        val b = OidcStateToken(OidcProperties(stateSecret = "  "), jwtProperties)
        val issued = a.generateLoginState()
        assertNotNull(b.validateLoginState(issued.state))
    }
}
