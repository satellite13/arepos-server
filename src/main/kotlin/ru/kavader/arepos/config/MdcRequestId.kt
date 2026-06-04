package ru.kavader.arepos.config

import org.slf4j.MDC
import java.util.*

object MdcRequestId {
    fun <T> withGeneratedIfMissing(prefix: String, block: () -> T): T {
        val existing = MDC.get(RequestIdFilter.MDC_KEY)
        if (!existing.isNullOrBlank()) {
            return block()
        }
        val generated = "$prefix-${UUID.randomUUID().toString().takeLast(12)}"
        MDC.put(RequestIdFilter.MDC_KEY, generated)
        try {
            return block()
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY)
        }
    }
}
