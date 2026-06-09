package ru.kavader.arepos.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import ru.kavader.arepos.config.AreposAuthProperties
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Service
class AuthCookieService(
    private val jwtTokenProvider: JwtTokenProvider,
    private val authProperties: AreposAuthProperties
) {
    private val secureRandom = SecureRandom()

    fun generateCsrfToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun writeAuthCookies(
        response: HttpServletResponse,
        accessToken: String,
        refreshToken: String,
        csrfToken: String
    ) {
        addCookie(response, buildCookie(AuthCookies.ACCESS, accessToken, "/", accessMaxAgeSeconds()))
        addCookie(response, buildCookie(AuthCookies.REFRESH, refreshToken, AuthCookies.REFRESH_PATH, refreshMaxAgeSeconds()))
        addCookie(response, buildCookie(AuthCookies.CSRF, csrfToken, "/", refreshMaxAgeSeconds(), httpOnly = false))
    }

    fun clearAuthCookies(response: HttpServletResponse) {
        addCookie(response, buildCookie(AuthCookies.ACCESS, "", "/", 0))
        addCookie(response, buildCookie(AuthCookies.REFRESH, "", AuthCookies.REFRESH_PATH, 0))
        addCookie(response, buildCookie(AuthCookies.CSRF, "", "/", 0, httpOnly = false))
    }

    private fun accessMaxAgeSeconds(): Long =
        jwtTokenProvider.accessExpirationSeconds().coerceAtLeast(1)

    private fun refreshMaxAgeSeconds(): Long =
        jwtTokenProvider.refreshExpirationSeconds().coerceAtLeast(1)

    private fun buildCookie(
        name: String,
        value: String,
        path: String,
        maxAgeSeconds: Long,
        httpOnly: Boolean = true
    ): ResponseCookie {
        val builder = ResponseCookie.from(name, value)
            .path(path)
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite("Lax")
        if (httpOnly) {
            builder.httpOnly(true)
        }
        if (authProperties.cookieSecure) {
            builder.secure(true)
        }
        return builder.build()
    }

    private fun addCookie(response: HttpServletResponse, cookie: ResponseCookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }
}
