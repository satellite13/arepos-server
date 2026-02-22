package ru.kavader.arepos.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.kavader.arepos.config.AuditInterceptor
import java.util.*

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {
    companion object {
        private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)
        val hasAuthHeader = request.getHeader("Authorization")?.startsWith("Bearer ") == true

        if (hasAuthHeader && token == null) {
            log.warn("JWT filter: malformed Authorization header, path={} {}", request.method, request.requestURI)
        }

        if (token != null) {
            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("JWT filter: token validation failed, path={} {}", request.method, request.requestURI)
            } else {
                val tokenType = jwtTokenProvider.getTokenType(token)
                if (tokenType == "access") {
                    val userId = jwtTokenProvider.getUserId(token)
                    val role = jwtTokenProvider.getRole(token)

                    log.debug(
                        "JWT filter: authenticated userId={}, role={}, path={} {}",
                        userId,
                        role,
                        request.method,
                        request.requestURI
                    )

                    val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
                    val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
                    SecurityContextHolder.getContext().authentication = authentication

                    AuditInterceptor.setCurrentUserId(userId)
                } else {
                    log.warn(
                        "JWT filter: non-access token type={} used for path={} {}",
                        tokenType,
                        request.method,
                        request.requestURI
                    )
                }
            }
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            AuditInterceptor.clearCurrentUserId()
        }
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) {
            header.substring(7)
        } else {
            null
        }
    }
}
