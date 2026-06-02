package ru.kavader.arepos.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import ru.kavader.arepos.repository.ModelSyncOutboxRepository
import java.time.Duration
import java.time.Instant

@Component("modelSyncOutbox")
class ModelSyncOutboxHealthIndicator(
    private val modelSyncOutboxRepository: ModelSyncOutboxRepository,
    @param:Value($$"${arepos.model-sync.outbox-enabled:false}") private val outboxEnabled: Boolean
) : HealthIndicator {
    override fun health(): Health {
        if (!outboxEnabled) {
            return Health.up()
                .withDetail("enabled", false)
                .build()
        }
        val pending = modelSyncOutboxRepository.countByPublishedAtIsNull()
        val oldestCreatedAt = modelSyncOutboxRepository.findOldestPendingCreatedAt()
        val oldestAgeSeconds = oldestCreatedAt?.let {
            Duration.between(it, Instant.now()).seconds
        }
        return Health.up()
            .withDetail("enabled", true)
            .withDetail("pendingRows", pending)
            .withDetail("oldestPendingAgeSeconds", oldestAgeSeconds ?: 0)
            .build()
    }
}
