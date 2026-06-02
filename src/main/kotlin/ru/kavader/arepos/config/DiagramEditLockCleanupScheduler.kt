package ru.kavader.arepos.config

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.service.DiagramEditLockService

@Component
class DiagramEditLockCleanupScheduler(
    private val diagramEditLockService: DiagramEditLockService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @Scheduled(fixedDelayString = "\${arepos.diagram-lock.cleanup-ms:45000}")
    fun deleteExpiredDiagramLocks() {
        MdcRequestId.withGeneratedIfMissing("diagram-lock-cleanup") {
            val deleted = diagramEditLockService.deleteExpiredLocks()
            if (deleted > 0) {
                logger.debug("Removed {} expired diagram edit lock(s)", deleted)
            }
        }
    }
}
