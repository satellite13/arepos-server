package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals

class LibraryIconNamesTest {
    @Test
    fun `normalizes filename and case`() {
        assertEquals("acme-app", LibraryIconNames.fromFilename("Acme App.SVG"))
        assertEquals("my_logo", LibraryIconNames.normalize("My_Logo"))
    }

    @Test
    fun `rejects empty after sanitize`() {
        assertThrows<ResponseStatusException> { LibraryIconNames.normalize("***") }
    }
}
