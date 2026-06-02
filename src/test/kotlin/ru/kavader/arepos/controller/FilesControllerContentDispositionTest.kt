package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilesControllerContentDispositionTest {

    @Test
    fun `buildInlineContentDisposition strips header injection vectors`() {
        val header = FilesController.buildInlineContentDisposition(
            "x\"; filename=\"loot.zip\r\nSet-Cookie: bad=1"
        )

        assertTrue(header.startsWith("inline; filename=\""))
        assertTrue(header.contains("filename*=UTF-8''"))
        assertFalse(header.contains("\r"))
        assertFalse(header.contains("\n"))
        assertFalse(header.contains("\"; filename=\"loot.zip"))
    }
}
