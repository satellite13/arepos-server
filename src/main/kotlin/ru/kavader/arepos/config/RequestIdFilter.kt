package ru.kavader.arepos.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

/**
 * Places a unique request ID into [MDC] for every HTTP request.
 * The ID can be referenced in log patterns via `%X{requestId}` and
 * is also added to the response header `X-Request-Id` for client-side correlation.
 */
@Component
class RequestIdFilter : OncePerRequestFilter() {

    companion object {
        const val MDC_KEY = "requestId"
        const val RESPONSE_HEADER = "X-Request-Id"
        private const val MAX_REQUEST_ID_LENGTH = 64
        private val REQUEST_ID_REGEX = Regex("^[A-Za-z0-9._-]+$")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = normalizeRequestId(request.getHeader(RESPONSE_HEADER))
            ?: UUID.randomUUID().toString().takeLast(12)
        MDC.put(MDC_KEY, requestId)
        response.setHeader(RESPONSE_HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    private fun normalizeRequestId(headerValue: String?): String? {
        val value = headerValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.length > MAX_REQUEST_ID_LENGTH) return null
        if (!REQUEST_ID_REGEX.matches(value)) return null
        return value
    }
}
