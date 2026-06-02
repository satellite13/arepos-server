package ru.kavader.arepos.config

import jakarta.annotation.PostConstruct
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

@Component
class AreposWebSocketPropertiesValidator(
    private val areposWebSocketProperties: AreposWebSocketProperties,
    private val environment: Environment
) {
    @PostConstruct
    fun validateForProdProfile() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return
        }
        val origins = areposWebSocketProperties.allowedOriginPatternArray()
        require(origins.isNotEmpty()) {
            "arepos.websocket.allowed-origin-patterns must be configured in prod profile"
        }
        require(origins.none { it == "*" }) {
            "arepos.websocket.allowed-origin-patterns must not contain '*' in prod profile"
        }
    }
}
