package ru.kavader.arepos.security

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import ru.kavader.arepos.dto.apikey.ApiKeyModes
import ru.kavader.arepos.dto.apikey.ApiKeyScopes
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpScopeFilterTest {

    private val filter = McpScopeFilter()

    @Test
    fun `non-mcp requests pass through`() {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest("GET", "/api/v1/users")
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        assertEquals(200, response.status)
    }

    @Test
    fun `mcp allows model paths with read scope`() {
        withMcp(setOf(ApiKeyScopes.MODELS_READ)) {
            val request = MockHttpServletRequest("GET", "/api/v1/models")
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, MockFilterChain())
            assertEquals(200, response.status)
        }
    }

    @Test
    fun `mcp denies admin and audit paths`() {
        withMcp(setOf(ApiKeyScopes.MODELS_READ, ApiKeyScopes.MODELS_WRITE)) {
            val request = MockHttpServletRequest("GET", "/api/v1/audit-log")
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, MockFilterChain())
            assertEquals(403, response.status)
            assertTrue(response.contentAsString.contains("path_not_allowed"))
        }
    }

    @Test
    fun `mcp denies write without write scope`() {
        withMcp(setOf(ApiKeyScopes.MODELS_READ)) {
            val request = MockHttpServletRequest("POST", "/api/v1/models")
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, MockFilterChain())
            assertEquals(403, response.status)
            assertTrue(response.contentAsString.contains("missing_scope"))
        }
    }

    private fun withMcp(scopes: Set<String>, block: () -> Unit) {
        val auth = UsernamePasswordAuthenticationToken(
            UUID.randomUUID(),
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        auth.details = McpAccessDetails(mode = ApiKeyModes.ALL, scopes = scopes, grants = null)
        SecurityContextHolder.getContext().authentication = auth
        try {
            block()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
