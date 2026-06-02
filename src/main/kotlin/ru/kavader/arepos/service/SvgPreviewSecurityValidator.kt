package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class SvgPreviewSecurityValidator {
    companion object {
        private val FORBIDDEN_PATTERNS = listOf(
            Regex("""<\s*script\b""", RegexOption.IGNORE_CASE),
            Regex("""<\s*foreignObject\b""", RegexOption.IGNORE_CASE),
            Regex("""<\s*iframe\b""", RegexOption.IGNORE_CASE),
            Regex("""<\s*object\b""", RegexOption.IGNORE_CASE),
            Regex("""<\s*embed\b""", RegexOption.IGNORE_CASE),
            Regex("""\son[a-z0-9_-]+\s*=""", RegexOption.IGNORE_CASE),
            Regex("""javascript\s*:""", RegexOption.IGNORE_CASE)
        )
    }

    fun validate(svgContent: String) {
        if (svgContent.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "SVG preview content must not be blank")
        }
        val hasUnsafeContent = FORBIDDEN_PATTERNS.any { it.containsMatchIn(svgContent) }
        if (hasUnsafeContent) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "SVG preview contains unsafe content and cannot be uploaded"
            )
        }
    }
}
