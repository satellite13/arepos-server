package ru.kavader.arepos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FeedbackPublicKeyTest {
    @Test
    fun `formats uppercase FB prefix`() {
        assertEquals("FB-12", FeedbackPublicKey.format(12))
    }

    @Test
    fun `parses key case-insensitively`() {
        assertEquals(12, FeedbackPublicKey.parseNumber("FB-12"))
        assertEquals(12, FeedbackPublicKey.parseNumber("fb-12"))
        assertEquals(12, FeedbackPublicKey.parseNumber("  FB-12  "))
    }

    @Test
    fun `parseNumber returns null for non-keys`() {
        assertNull(FeedbackPublicKey.parseNumber("12"))
        assertNull(FeedbackPublicKey.parseNumber("IDEA-1"))
        assertNull(FeedbackPublicKey.parseNumber("not-a-key"))
    }

    @Test
    fun `parsePlainNumber accepts digits only`() {
        assertEquals(12, FeedbackPublicKey.parsePlainNumber("12"))
        assertNull(FeedbackPublicKey.parsePlainNumber("FB-12"))
        assertNull(FeedbackPublicKey.parsePlainNumber("12a"))
    }
}
