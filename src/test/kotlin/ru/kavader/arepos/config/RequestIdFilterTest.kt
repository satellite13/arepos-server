package ru.kavader.arepos.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestIdFilterTest {

    private val filter = RequestIdFilter()

    @Test
    fun `keeps valid request id header`() {
        val request = MockHttpServletRequest("GET", "/api/v1/health").apply {
            addHeader(RequestIdFilter.RESPONSE_HEADER, "abc-123_DEF.trace")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals("abc-123_DEF.trace", response.getHeader(RequestIdFilter.RESPONSE_HEADER))
    }

    @Test
    fun `replaces invalid request id header`() {
        val request = MockHttpServletRequest("GET", "/api/v1/health").apply {
            addHeader(RequestIdFilter.RESPONSE_HEADER, "bad value with spaces")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        val generated = response.getHeader(RequestIdFilter.RESPONSE_HEADER)
        assertNotNull(generated)
        val generatedValue = requireNotNull(generated)
        assertTrue(generatedValue.length == 12)
        assertTrue(generatedValue.matches(Regex("^[a-z0-9-]{12}$")))
    }
}
