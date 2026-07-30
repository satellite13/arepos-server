package ru.kavader.arepos.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OidcPropertiesTest {

    @Test
    fun `auto mode disabled when credentials missing`() {
        val props = OidcProperties(enabled = "auto")
        assertFalse(props.isConfigured())
        assertFalse(props.isEffectivelyEnabled())
    }

    @Test
    fun `auto mode enabled when credentials present`() {
        val props = configured(enabled = "auto")
        assertTrue(props.isConfigured())
        assertTrue(props.isEffectivelyEnabled())
    }

    @Test
    fun `explicit false wins over configured credentials`() {
        val props = configured(enabled = "false")
        assertTrue(props.isConfigured())
        assertFalse(props.isEffectivelyEnabled())
    }

    @Test
    fun `explicit true enables even with incomplete config`() {
        val props = OidcProperties(enabled = "true")
        assertTrue(props.isEffectivelyEnabled())
    }

    @Test
    fun `displayName defaults to SSO`() {
        assertEquals("SSO", OidcProperties().displayName)
    }

    private fun configured(enabled: String) = OidcProperties(
        enabled = enabled,
        issuerUri = "https://idp.example.com/realms/app/",
        clientId = "client",
        clientSecret = "secret",
        redirectUri = "https://app.example.com/auth/oidc/callback"
    )
}
