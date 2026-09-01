package ru.kavader.arepos.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.kavader.arepos.dto.apikey.ApiKeyScopes

/**
 * Enforces API-key scopes for MCP access tokens and confines them to model APIs.
 * Regular access tokens are unaffected.
 */
@Component
class McpScopeFilter : OncePerRequestFilter() {

    companion object {
        /** Paths MCP tokens may call (prefix match). Auth/actuator/ws are excluded via shouldNotFilter. */
        private val ALLOWED_PREFIXES = listOf(
            "/api/v1/models",
            "/api/v1/nodes",
            "/api/v1/links",
            "/api/v1/diagrams",
            "/api/v1/diagram-locks",
            "/api/v1/notations",
            "/api/v1/node-types",
            "/api/v1/link-types",
            "/api/v1/components",
            "/api/v1/relations",
            "/api/v1/relation-rules",
            "/api/v1/node-shapes",
            "/api/v1/validation-scripts",
            "/api/v1/files",
            "/api/v1/documents",
            "/api/v1/library-icons",
            "/api/v1/search"
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/api/v1/auth/") ||
            path.startsWith("/actuator/") ||
            path.startsWith("/ws")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val details = CurrentUser.mcpAccessDetails()
        if (details == null) {
            filterChain.doFilter(request, response)
            return
        }

        val path = request.requestURI
        if (path.startsWith("/api/v1/api-keys")) {
            writeForbidden(response, "missing_scope", "API key management requires a user session token")
            return
        }

        if (!isAllowedPath(path)) {
            writeForbidden(response, "path_not_allowed", "MCP tokens cannot access this endpoint")
            return
        }

        val method = request.method.uppercase()
        val requiredScope = when (method) {
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name() -> ApiKeyScopes.MODELS_READ
            else -> ApiKeyScopes.MODELS_WRITE
        }

        if (!details.hasScopeSomewhere(requiredScope)) {
            writeForbidden(response, "missing_scope", "Missing required scope: $requiredScope")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun isAllowedPath(path: String): Boolean =
        ALLOWED_PREFIXES.any { prefix -> path == prefix || path.startsWith("$prefix/") }

    private fun writeForbidden(response: HttpServletResponse, code: String, message: String) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"error":"$code","message":"$message"}""")
    }
}
