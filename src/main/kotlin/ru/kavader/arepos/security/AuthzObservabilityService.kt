package ru.kavader.arepos.security

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class AuthzObservabilityService(
    private val meterRegistry: MeterRegistry
) {
    fun recordCerbosRequest(resourceKind: String, action: String, outcome: String, durationNanos: Long) {
        meterRegistry.timer(
            "arepos.authz.cerbos.request",
            "resource_kind",
            resourceKind,
            "action",
            action,
            "outcome",
            outcome
        ).record(durationNanos, TimeUnit.NANOSECONDS)
    }

    fun recordFinalDecision(resourceKind: String, action: String, source: String, allowed: Boolean) {
        meterRegistry.counter(
            "arepos.authz.final.decision",
            "resource_kind",
            resourceKind,
            "action",
            action,
            "source",
            source,
            "allowed",
            allowed.toString()
        ).increment()
    }
}
