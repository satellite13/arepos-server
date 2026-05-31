package ru.kavader.arepos.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

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
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = request.getHeader(RESPONSE_HEADER)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().takeLast(12)
        MDC.put(MDC_KEY, requestId)
        response.setHeader(RESPONSE_HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
