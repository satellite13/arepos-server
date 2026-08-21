package ru.kavader.arepos.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.PageRequest
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer

@Configuration
class PageableWebConfig {
    @Bean
    fun pageableResolverCustomizer(
        @Value("\${spring.data.web.pageable.max-page-size:25000}") maxPageSize: Int,
        @Value("\${spring.data.web.pageable.default-page-size:50}") defaultPageSize: Int
    ): PageableHandlerMethodArgumentResolverCustomizer =
        PageableHandlerMethodArgumentResolverCustomizer { resolver ->
            resolver.setMaxPageSize(maxPageSize)
            resolver.setFallbackPageable(PageRequest.of(0, defaultPageSize))
        }
}
