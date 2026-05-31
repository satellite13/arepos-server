package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import ru.kavader.arepos.metrics.ModelSyncMetrics
import ru.kavader.arepos.repository.ModelSyncOutboxRepository
import java.time.Instant

@Service
class ModelSyncOutboxPublishService(
    private val outboxRepository: ModelSyncOutboxRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
    private val metrics: ModelSyncMetrics,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${arepos.model-sync.outbox-enabled:false}") private val enabled: Boolean,
    @Value("\${arepos.model-sync.outbox-batch-size:50}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val maxAttempts = 25

    /**
     * Publishes pending outbox rows. Each row is processed in its own transaction
     * so that a failure in one row does not roll back updates to other rows.
     * The STOMP message send happens inside the per-row transaction to ensure
     * atomicity: either the message is sent AND the row is marked published,
     * or the row is marked as failed and made available for retry.
     */
    fun publishPending() {
        if (!enabled) {
            return
        }
        metrics.refreshPendingCount(outboxRepository.countByPublishedAtIsNull())
        val batch = outboxRepository.findPendingForPublish(PageRequest.of(0, batchSize))
        val now = Instant.now()
        for (row in batch) {
            transactionTemplate.executeWithoutResult {
                processRow(row, now)
            }
        }
    }

    private fun processRow(row: ru.kavader.arepos.model.ModelSyncOutbox, now: Instant) {
        val modelId = requireNotNull(row.model.id)
        try {
            @Suppress("UNCHECKED_CAST")
            val map = objectMapper.readValue(row.payload, Map::class.java) as Map<String, Any?>
            messagingTemplate.convertAndSend("/topic/models/$modelId", map)
            row.publishedAt = now
            row.lastError = null
        } catch (ex: Exception) {
            log.warn("model sync outbox publish failed: id={}", row.id, ex)
            metrics.outboxRetries.increment()
            row.attempts += 1
            row.lastError = ex.message?.take(2000)
            if (row.attempts == maxAttempts) {
                metrics.outboxPublishFailures.increment()
            }
        }
    }
}
