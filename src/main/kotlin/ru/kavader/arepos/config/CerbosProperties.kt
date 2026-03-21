package ru.kavader.arepos.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

enum class CerbosMode {
    DISABLED,
    SHADOW,
    ENFORCE
}

@ConfigurationProperties(prefix = "arepos.authz.cerbos")
data class CerbosProperties(
    val enabled: Boolean = false,
    val mode: CerbosMode = CerbosMode.DISABLED,
    val endpoint: String = "http://localhost:3592",
    val requestTimeout: Duration = Duration.ofMillis(200),
    val failOpen: Boolean = false,
    val shadowEnabled: Boolean = false,
    val enforceEnabled: Boolean = false,
    val bundleVersion: String = ""
)
