package ru.kavader.arepos.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "arepos.oidc")
data class OidcProperties(
    /**
     * `true` / `false` / `auto` (default).
     * In `auto` mode SSO is enabled only when issuer, client id/secret and redirect URI are set.
     */
    val enabled: String = "auto",
    val issuerUri: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val postLogoutUri: String = "",
    val frontendUrl: String = "",
    val scope: String = "openid profile email",
    /** Provider name shown on the login button (e.g. "Lemanapro", "Keycloak"). */
    val displayName: String = "SSO",
    val stateSecret: String = ""
) {
    fun isConfigured(): Boolean =
        issuerUri.isNotBlank() &&
            clientId.isNotBlank() &&
            clientSecret.isNotBlank() &&
            redirectUri.isNotBlank()

    fun isEffectivelyEnabled(): Boolean =
        when (enabled.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> isConfigured()
        }
}
