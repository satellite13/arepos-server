package ru.kavader.arepos.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.kavader.arepos.config.AreposAuthProperties
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class CsrfFilter(
    private val authProperties: AreposAuthProperties
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!authProperties.csrfEnabled) {
            return true
        }
        val method = request.method.uppercase()
        if (method == HttpMethod.GET.name() || method == HttpMethod.HEAD.name() || method == HttpMethod.OPTIONS.name()) {
            return true
        }
        val path = request.requestURI
        if (path.startsWith("/ws") || path.startsWith("/actuator/")) {
            return true
        }
        if (path.startsWith("/api/v1/auth/login") ||
            path.startsWith("/api/v1/auth/register") ||
            path.startsWith("/api/v1/auth/refresh") ||
            path.startsWith("/api/v1/auth/register-admin") ||
            path.startsWith("/api/v1/auth/sso") ||
            path.startsWith("/api/v1/auth/api-keys/exchange")
        ) {
            return true
        }
        // API clients (MCP JWT, user Bearer) do not use cookie CSRF double-submit —
        // but only when there is no browser access cookie (otherwise CSRF still applies).
        val authorization = request.getHeader("Authorization")?.trim().orEmpty()
        if (authorization.startsWith("Bearer ", ignoreCase = true) && !hasAccessCookie(request)) {
            return true
        }
        return false
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val cookieToken = request.cookies
            ?.firstOrNull { it.name == AuthCookies.CSRF }
            ?.value
            ?.takeIf { it.isNotBlank() }
        val headerToken = request.getHeader(AuthCookies.CSRF_HEADER)?.trim()?.takeIf { it.isNotEmpty() }

        if (cookieToken == null || headerToken == null || !csrfTokensEqual(cookieToken, headerToken)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token missing or invalid")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun hasAccessCookie(request: HttpServletRequest): Boolean =
        request.cookies
            ?.any { it.name == AuthCookies.ACCESS && !it.value.isNullOrBlank() } == true

    private fun csrfTokensEqual(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(StandardCharsets.UTF_8)
        val bBytes = b.toByteArray(StandardCharsets.UTF_8)
        if (aBytes.size != bBytes.size) {
            // Keep a constant-time compare path even on length mismatch, then fail.
            MessageDigest.isEqual(aBytes, aBytes)
            return false
        }
        return MessageDigest.isEqual(aBytes, bBytes)
    }
}
