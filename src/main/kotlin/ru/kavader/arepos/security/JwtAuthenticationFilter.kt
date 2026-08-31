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
import ru.kavader.arepos.dto.apikey.ApiKeyModes
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.UsersRepository

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val usersRepository: UsersRepository
) : OncePerRequestFilter() {
    companion object {
        private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Defensive cleanup for thread-pooled executors: never reuse stale audit user id.
        AuditInterceptor.clearCurrentUserId()
        try {
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
                    when (tokenType) {
                        TokenType.ACCESS, TokenType.MCP_ACCESS -> authenticateAccessToken(token, tokenType, request)
                        else -> {
                            log.warn(
                                "JWT filter: non-access token type={} used for path={} {}",
                                tokenType,
                                request.method,
                                request.requestURI
                            )
                        }
                    }
                }
            }
            filterChain.doFilter(request, response)
        } finally {
            AuditInterceptor.clearCurrentUserId()
        }
    }

    private fun authenticateAccessToken(token: String, tokenType: TokenType, request: HttpServletRequest) {
        val userId = jwtTokenProvider.getUserId(token)
        val user = usersRepository.findById(userId).orElse(null)
        if (user == null) {
            log.warn(
                "JWT filter: user not found for token subject={}, path={} {}",
                userId,
                request.method,
                request.requestURI
            )
            return
        }
        if (!user.isActive) {
            log.warn(
                "JWT filter: inactive userId={} blocked, path={} {}",
                userId,
                request.method,
                request.requestURI
            )
            return
        }

        // MCP tokens must not inherit ADMIN/EDITOR Cerbos privileges (admin_panel, user_admin, …).
        val role = if (tokenType == TokenType.MCP_ACCESS) {
            Role.USER.name
        } else {
            user.role.name
        }
        log.debug(
            "JWT filter: authenticated userId={}, role={}, tokenType={}, path={} {}",
            userId,
            role,
            tokenType,
            request.method,
            request.requestURI
        )

        val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
        val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
        if (tokenType == TokenType.MCP_ACCESS) {
            val mode = jwtTokenProvider.getMcpMode(token).orEmpty()
            authentication.details = McpAccessDetails(
                mode = mode,
                scopes = if (mode == ApiKeyModes.ALL) jwtTokenProvider.getScopes(token) else null,
                grants = if (mode == ApiKeyModes.GRANTS) {
                    jwtTokenProvider.getMcpGrants(token)
                        ?.associate { it.modelId to it.scopes.toSet() }
                        ?: emptyMap()
                } else {
                    null
                }
            )
        }
        SecurityContextHolder.getContext().authentication = authentication
        AuditInterceptor.setCurrentUserId(userId)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val cookieToken = request.cookies
            ?.firstOrNull { it.name == AuthCookies.ACCESS }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (cookieToken != null) {
            return cookieToken
        }
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) {
            header.substring(7)
        } else {
            null
        }
    }
}
