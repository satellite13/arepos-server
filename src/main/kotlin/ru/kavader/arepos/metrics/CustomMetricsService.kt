package ru.kavader.arepos.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class CustomMetricsService(
    private val meterRegistry: MeterRegistry
) {
    // Auth
    val authLoginSuccess: Counter = Counter.builder("arepos_auth_login_success_total")
        .description("Successful login attempts")
        .register(meterRegistry)

    val authLoginFailure: Counter = Counter.builder("arepos_auth_login_failure_total")
        .description("Failed login attempts")
        .register(meterRegistry)

    // Diagram edit locks
    val editLockAcquire: Counter = Counter.builder("arepos_diagram_edit_lock_acquire_total")
        .description("Diagram edit lock acquire attempts")
        .register(meterRegistry)

    val editLockRelease: Counter = Counter.builder("arepos_diagram_edit_lock_release_total")
        .description("Diagram edit lock releases")
        .register(meterRegistry)

    val httpServer5xx: Counter = Counter.builder("arepos_http_server_5xx_total")
        .description("HTTP responses with 5xx status codes")
        .register(meterRegistry)

    fun recordBatchSaveDuration(outcome: String, durationNanos: Long) {
        meterRegistry.timer(
            "arepos_batch_save_duration",
            "outcome",
            outcome
        )
            .record(durationNanos, TimeUnit.NANOSECONDS)
    }

}
