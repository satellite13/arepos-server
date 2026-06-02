package ru.kavader.arepos.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

class AreposWebSocketPropertiesValidatorTest {
    @Test
    fun `allows wildcard origin outside prod`() {
        val env = MockEnvironment().withProperty("spring.profiles.active", "dev")
        val validator = AreposWebSocketPropertiesValidator(
            areposWebSocketProperties = AreposWebSocketProperties(allowedOriginPatterns = "*"),
            environment = env
        )

        assertDoesNotThrow { validator.validateForProdProfile() }
    }

    @Test
    fun `rejects wildcard origin in prod`() {
        val env = MockEnvironment().withProperty("spring.profiles.active", "prod")
        val validator = AreposWebSocketPropertiesValidator(
            areposWebSocketProperties = AreposWebSocketProperties(allowedOriginPatterns = "*"),
            environment = env
        )

        assertThrows<IllegalArgumentException> { validator.validateForProdProfile() }
    }

    @Test
    fun `rejects mixed wildcard list in prod`() {
        val env = MockEnvironment().withProperty("spring.profiles.active", "prod")
        val validator = AreposWebSocketPropertiesValidator(
            areposWebSocketProperties = AreposWebSocketProperties(
                allowedOriginPatterns = "https://app.example.com,*"
            ),
            environment = env
        )

        assertThrows<IllegalArgumentException> { validator.validateForProdProfile() }
    }

    @Test
    fun `allows explicit origin list in prod`() {
        val env = MockEnvironment().withProperty("spring.profiles.active", "prod")
        val validator = AreposWebSocketPropertiesValidator(
            areposWebSocketProperties = AreposWebSocketProperties(
                allowedOriginPatterns = "https://app.example.com,https://admin.example.com"
            ),
            environment = env
        )

        assertDoesNotThrow { validator.validateForProdProfile() }
    }
}
