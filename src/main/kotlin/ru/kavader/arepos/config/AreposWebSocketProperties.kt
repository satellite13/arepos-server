package ru.kavader.arepos.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "arepos.websocket")
data class AreposWebSocketProperties(
    /** Список через запятую или одна `*` для разработки. */
    val allowedOriginPatterns: String = "*"
) {
    fun allowedOriginPatternArray(): Array<String> {
        val p = allowedOriginPatterns.trim()
        if (p == "*" || p.isEmpty()) {
            return arrayOf("*")
        }
        return p.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
    }
}
