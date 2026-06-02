package ru.kavader.arepos.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.repository.AuditLogRepository
import java.time.Duration
import java.time.Instant

@Component
class AuditRetentionScheduler(
    private val auditLogRepository: AuditLogRepository,
    @param:Value($$"${arepos.audit.retention:PT24H}")
    private val retention: Duration
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @Scheduled(cron = $$"${arepos.audit.cleanup-cron:0 0 * * * *}")
    fun cleanupExpiredAuditLogs() {
        MdcRequestId.withGeneratedIfMissing("audit-cleanup") {
            val cutoff = Instant.now().minus(retention)
            val deletedRows = auditLogRepository.deleteByChangedAtBefore(cutoff)
            if (deletedRows > 0) {
                logger.info("Deleted {} audit log rows older than {}", deletedRows, cutoff)
            }
        }
    }
}
