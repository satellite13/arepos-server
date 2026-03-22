package ru.kavader.arepos.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "arepos.authz.cerbos")
data class CerbosProperties(
    val endpoint: String = "http://localhost:3592",
    val requestTimeout: Duration = Duration.ofMillis(200),
    val bundleVersion: String = ""
)
