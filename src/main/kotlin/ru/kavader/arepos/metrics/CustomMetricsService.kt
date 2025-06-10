package ru.kavader.arepos.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class CustomMetricsService(meterRegistry: MeterRegistry) {
    val counter = Counter.builder("hello_world_counter")
        .description("Test rest method counter")
        .tag("hello_world", "testing")
        .register(meterRegistry)

    fun incrementHelloWorldCounter() {
        counter.increment()
    }
}