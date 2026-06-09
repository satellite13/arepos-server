package ru.kavader.arepos.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordPolicyValidatorTest {

    private val validator = PasswordPolicyValidator()

    @Test
    fun `accepts strong password`() {
        assertTrue(validator.validate("ValidPass1", "user@test.com").isEmpty())
    }

    @Test
    fun `rejects short password`() {
        val violations = validator.validate("Ab1", "user@test.com")
        assertTrue(violations.any { it.contains("at least") })
    }

    @Test
    fun `rejects password matching email`() {
        val violations = validator.validate("User@test.com", "user@test.com")
        assertTrue(violations.any { it.contains("email") })
    }

    @Test
    fun `rejects common password`() {
        val violations = validator.validate("Password1", "user@test.com")
        assertTrue(violations.any { it.contains("common") })
    }
}
