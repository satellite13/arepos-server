package ru.kavader.arepos.config

import org.junit.jupiter.api.Test
import org.slf4j.MDC
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MdcRequestIdTest {
    @Test
    fun `sets and clears generated request id when missing`() {
        MDC.remove(RequestIdFilter.MDC_KEY)

        var insideValue: String?
        MdcRequestId.withGeneratedIfMissing("test-job") {
            insideValue = MDC.get(RequestIdFilter.MDC_KEY)
            assertNotNull(insideValue)
            assertTrue(insideValue.startsWith("test-job-"))
        }

        assertNull(MDC.get(RequestIdFilter.MDC_KEY))
    }

    @Test
    fun `keeps existing request id unchanged`() {
        MDC.put(RequestIdFilter.MDC_KEY, "existing-request-id")
        try {
            MdcRequestId.withGeneratedIfMissing("ignored-prefix") {
                assertEquals("existing-request-id", MDC.get(RequestIdFilter.MDC_KEY))
            }
            assertEquals("existing-request-id", MDC.get(RequestIdFilter.MDC_KEY))
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY)
        }
    }
}
