package ru.kavader.arepos.config

import org.springframework.boot.context.properties.ConfigurationProperties
import ru.kavader.arepos.model.Role

@ConfigurationProperties(prefix = "arepos.auth")
data class AreposAuthProperties(
    /** Open user self-registration (disable in production). */
    val registrationEnabled: Boolean = true,
    /** Role assigned to newly registered users (demo stands may raise it to architect). */
    val defaultRole: Role = Role.reader,
    /** HttpOnly cookie Secure flag (true in production behind HTTPS). */
    val cookieSecure: Boolean = false,
    /** Double-submit CSRF for cookie-based browser sessions. */
    val csrfEnabled: Boolean = true,
    /**
     * Optional cookie Domain for cross-subdomain SSO (e.g. `.example.com`).
     * Empty = host-only cookies.
     */
    val cookieDomain: String = "",
    /**
     * Comma-separated browser origins allowed for credentialed CORS
     * (e.g. `https://www.example.com,http://localhost:5174`).
     */
    val corsAllowedOrigins: String = ""
)
