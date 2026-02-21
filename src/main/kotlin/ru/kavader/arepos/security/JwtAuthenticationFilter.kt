package ru.kavader.arepos.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)

        if (token != null && jwtTokenProvider.validateToken(token)) {
            val tokenType = jwtTokenProvider.getTokenType(token)
            if (tokenType == "access") {
                val userId = jwtTokenProvider.getUserId(token)
                val role = jwtTokenProvider.getRole(token)

                val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
                val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
                SecurityContextHolder.getContext().authentication = authentication

                AuditInterceptor.setCurrentUserId(userId)
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
