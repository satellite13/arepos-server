package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import ru.kavader.arepos.config.MdcRequestId
import ru.kavader.arepos.metrics.ModelSyncMetrics
import ru.kavader.arepos.repository.ModelSyncOutboxRepository
import java.time.Instant
import java.util.*

@Service
class ModelSyncOutboxPublishService(
    private val outboxRepository: ModelSyncOutboxRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
    private val metrics: ModelSyncMetrics,
    private val transactionTemplate: TransactionTemplate,
    @param:Value($$"${arepos.model-sync.outbox-enabled:false}") private val enabled: Boolean,
    @param:Value($$"${arepos.model-sync.outbox-batch-size:50}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val maxAttempts = 25

    private data class PendingOutboxRow(
        val id: UUID,
        val modelId: UUID,
        val payload: String
    )

    /**
     * Publishes pending outbox rows. Each row is read and updated in short transactions.
     * STOMP send is intentionally outside DB transaction to avoid holding JDBC connection
     * while broker dispatch is in progress.
     */
    fun publishPending() {
        MdcRequestId.withGeneratedIfMissing("outbox-publish") {
            if (!enabled) {
                return@withGeneratedIfMissing
            }
            val batchIds = transactionTemplate.execute {
                val pendingCount = outboxRepository.countByPublishedAtIsNull()
                metrics.refreshPendingCount(pendingCount)
                outboxRepository.findPendingForPublish(PageRequest.of(0, batchSize))
                    .mapNotNull { it.id }
            }.orEmpty()
            val now = Instant.now()
            for (rowId in batchIds) {
                processRow(rowId, now)
            }
        }
    }

    private fun processRow(rowId: UUID, now: Instant) {
        val row = loadPendingRow(rowId) ?: return
        val payload = try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(row.payload, Map::class.java) as Map<String, Any?>
        } catch (ex: Exception) {
            registerPublishFailure(row.id, ex)
            return
        }
        try {
            messagingTemplate.convertAndSend("/topic/models/${row.modelId}", payload)
            markPublished(row.id, now)
        } catch (ex: Exception) {
            registerPublishFailure(row.id, ex)
        }
    }

    private fun loadPendingRow(rowId: UUID): PendingOutboxRow? {
        return transactionTemplate.execute {
            val row = outboxRepository.findById(rowId).orElse(null) ?: return@execute null
            if (row.publishedAt != null) {
                return@execute null
            }
            PendingOutboxRow(
                id = requireNotNull(row.id),
                modelId = requireNotNull(row.model.id),
                payload = row.payload
            )
        }
    }

    private fun markPublished(rowId: UUID, now: Instant) {
        transactionTemplate.executeWithoutResult {
            val row = outboxRepository.findById(rowId).orElse(null) ?: return@executeWithoutResult
            if (row.publishedAt != null) {
                return@executeWithoutResult
            }
            row.publishedAt = now
            row.lastError = null
            outboxRepository.save(row)
        }
    }

    private fun registerPublishFailure(rowId: UUID, ex: Exception) {
        log.warn("model sync outbox publish failed: id={}", rowId, ex)
        metrics.outboxRetries.increment()
        var reachedMaxAttempts = false
        transactionTemplate.executeWithoutResult {
            val row = outboxRepository.findById(rowId).orElse(null) ?: return@executeWithoutResult
            if (row.publishedAt != null) {
                return@executeWithoutResult
            }
            row.attempts += 1
            row.lastError = ex.message?.take(2000)
            reachedMaxAttempts = row.attempts == maxAttempts
            outboxRepository.save(row)
        }
        if (reachedMaxAttempts) {
            metrics.outboxPublishFailures.increment()
        }
    }
}
