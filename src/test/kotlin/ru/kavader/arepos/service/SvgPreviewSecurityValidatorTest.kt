package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertFailsWith

class SvgPreviewSecurityValidatorTest {
    private val validator = SvgPreviewSecurityValidator()

    @Test
    fun `allows simple safe svg`() {
        validator.validate(
            """
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32">
              <rect x="2" y="2" width="28" height="28" fill="#7c5cfc"/>
            </svg>
            """.trimIndent()
        )
    }

    @Test
    fun `rejects script tag in svg`() {
        assertFailsWith<ResponseStatusException> {
            validator.validate(
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <script>alert('xss')</script>
                </svg>
                """.trimIndent()
            )
        }
    }

    @Test
    fun `rejects event handler attributes`() {
        assertFailsWith<ResponseStatusException> {
            validator.validate(
                """
                <svg xmlns="http://www.w3.org/2000/svg" onload="alert('xss')">
                  <circle cx="10" cy="10" r="8"/>
                </svg>
                """.trimIndent()
            )
        }
    }

    @Test
    fun `rejects javascript url payload`() {
        assertFailsWith<ResponseStatusException> {
            validator.validate(
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <a href="javascript:alert('xss')">click</a>
                </svg>
                """.trimIndent()
            )
        }
    }
}
