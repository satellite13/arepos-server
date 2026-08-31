package ru.kavader.arepos.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import ru.kavader.arepos.config.AreposAuthProperties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class TestableCsrfFilter(
    authProperties: AreposAuthProperties
) : CsrfFilter(authProperties) {
    fun shouldSkip(request: HttpServletRequest): Boolean = shouldNotFilter(request)
}

class CsrfFilterTest {

    private val filter = TestableCsrfFilter(AreposAuthProperties(csrfEnabled = true))

    @Test
    fun `skips CSRF for Bearer without access cookie`() {
        val request = MockHttpServletRequest("POST", "/api/v1/models")
        request.addHeader("Authorization", "Bearer some-token")
        assertTrue(filter.shouldSkip(request))
    }

    @Test
    fun `does not skip CSRF when Bearer and access cookie are both present`() {
        val request = MockHttpServletRequest("POST", "/api/v1/models")
        request.addHeader("Authorization", "Bearer some-token")
        request.setCookies(Cookie(AuthCookies.ACCESS, "access-jwt"))
        assertFalse(filter.shouldSkip(request))
    }

    @Test
    fun `allows request when cookie and header CSRF tokens match`() {
        val request = MockHttpServletRequest("POST", "/api/v1/models")
        request.setCookies(Cookie(AuthCookies.CSRF, "csrf-token-value"))
        request.addHeader(AuthCookies.CSRF_HEADER, "csrf-token-value")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `rejects request when CSRF tokens differ`() {
        val request = MockHttpServletRequest("POST", "/api/v1/models")
        request.setCookies(Cookie(AuthCookies.CSRF, "cookie-token"))
        request.addHeader(AuthCookies.CSRF_HEADER, "header-token")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        assertEquals(403, response.status)
        verify(chain, never()).doFilter(request, response)
    }

    @Test
    fun `rejects request when CSRF token lengths differ`() {
        val request = MockHttpServletRequest("POST", "/api/v1/models")
        request.setCookies(Cookie(AuthCookies.CSRF, "short"))
        request.addHeader(AuthCookies.CSRF_HEADER, "much-longer-token")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        assertEquals(403, response.status)
        verify(chain, never()).doFilter(request, response)
    }
}
