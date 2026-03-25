package ru.kavader.arepos.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class ModelSyncMetrics(meterRegistry: MeterRegistry) {
    private val pendingGauge = AtomicLong(0)

    val outboxPublishFailures: Counter = Counter.builder("arepos_model_sync_outbox_publish_failures_total")
        .description("Failed attempts to publish a model sync outbox row to STOMP")
        .register(meterRegistry)

    val outboxRetries: Counter = Counter.builder("arepos_model_sync_outbox_retries_total")
        .description("Outbox publish retries (incremented on each failed attempt)")
        .register(meterRegistry)

    init {
        Gauge.builder("arepos_model_sync_outbox_pending_rows") { pendingGauge.get().toDouble() }
            .description("Approximate pending model sync outbox rows (updated each publish tick)")
            .register(meterRegistry)
    }

    fun refreshPendingCount(pending: Long) {
        pendingGauge.set(pending)
    }
}
