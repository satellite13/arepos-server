package ru.kavader.arepos.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "arepos.swagger")
data class AreposSwaggerProperties(
    /** Expose Swagger UI and OpenAPI docs without authentication. */
    val enabled: Boolean = true
)
