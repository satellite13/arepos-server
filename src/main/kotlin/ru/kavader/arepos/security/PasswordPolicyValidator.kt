package ru.kavader.arepos.security

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class PasswordPolicyValidator {
    private val commonPasswords: Set<String> = loadCommonPasswords()

    fun validateOrThrow(password: String, email: String) {
        val violations = validate(password, email)
        if (violations.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                violations.first()
            )
        }
    }

    fun validate(password: String, email: String): List<String> {
        val violations = mutableListOf<String>()
        if (password.length < MIN_LENGTH) {
            violations.add("Password must be at least $MIN_LENGTH characters")
        }
        if (!password.any { it.isUpperCase() }) {
            violations.add("Password must contain at least one uppercase letter")
        }
        if (!password.any { it.isLowerCase() }) {
            violations.add("Password must contain at least one lowercase letter")
        }
        if (!password.any { it.isDigit() }) {
            violations.add("Password must contain at least one digit")
        }
        val normalizedEmail = email.trim().lowercase()
        val normalizedPassword = password.lowercase()
        if (normalizedEmail.isNotEmpty() &&
            (normalizedPassword == normalizedEmail || normalizedEmail.contains(normalizedPassword))
        ) {
            violations.add("Password must not match or contain the email address")
        }
        if (commonPasswords.contains(normalizedPassword)) {
            violations.add("Password is too common")
        }
        return violations
    }

    private fun loadCommonPasswords(): Set<String> {
        val stream = javaClass.getResourceAsStream("/security/common-passwords.txt")
            ?: return emptySet()
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        }
    }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_FAILED_ATTEMPTS = 10
        const val LOCKOUT_MINUTES = 15L
    }
}
