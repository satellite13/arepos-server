package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SvgSanitizerTest {
    private val sanitizer = SvgSanitizer()

    @Test
    fun `keeps path and viewBox`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M1 2h10" fill="#111"/></svg>"""
        val out = sanitizer.sanitize(svg)
        assertContains(out, "viewBox")
        assertContains(out, "M1 2h10")
        assertFalse(out.contains("<script", ignoreCase = true))
    }

    @Test
    fun `strips script and event handlers`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10" onclick="alert(1)"><script>alert(1)</script><path d="M0 0h1"/></svg>"""
        val out = sanitizer.sanitize(svg)
        assertFalse(out.contains("script", ignoreCase = true))
        assertFalse(out.contains("onclick", ignoreCase = true))
        assertContains(out, "path")
    }

    @Test
    fun `rejects doctype`() {
        val svg = """<!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><svg xmlns="http://www.w3.org/2000/svg"></svg>"""
        assertThrows<ResponseStatusException> { sanitizer.sanitize(svg) }
    }

    @Test
    fun `rejects blank and non-svg`() {
        assertThrows<ResponseStatusException> { sanitizer.sanitize("   ") }
        assertThrows<ResponseStatusException> { sanitizer.sanitize("<html></html>") }
    }

    @Test
    fun `hash is stable`() {
        val svg = sanitizer.sanitize("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1"/></svg>""")
        assertTrue(sanitizer.contentHash(svg).length == 64)
        assertTrue(sanitizer.contentHash(svg) == sanitizer.contentHash(svg))
    }
}
