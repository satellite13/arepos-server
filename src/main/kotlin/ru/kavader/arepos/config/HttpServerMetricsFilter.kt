package ru.kavader.arepos.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.kavader.arepos.metrics.CustomMetricsService

@Component
class HttpServerMetricsFilter(
    private val metrics: CustomMetricsService
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            filterChain.doFilter(request, response)
        } finally {
            if (response.status >= 500) {
                metrics.httpServer5xx.increment()
            }
        }
    }
}
