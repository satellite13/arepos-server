package ru.kavader.arepos.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.kavader.arepos.service.modelpackage.ModelPackageImportJobService
import java.time.Duration

@Component
class PackageImportJobCleanupScheduler(
    private val jobService: ModelPackageImportJobService,
    @Value("\${arepos.package-import.retention:PT24H}")
    private val retention: Duration,
    @Value("\${arepos.package-import.running-timeout:PT60M}")
    private val runningTimeout: Duration
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${arepos.package-import.cleanup-cron:0 15 * * * *}")
    fun cleanupPackageImportJobs() {
        MdcRequestId.withGeneratedIfMissing("package-import-cleanup") {
            val timedOut = jobService.failStaleRunningJobs(runningTimeout)
            if (timedOut > 0) {
                logger.info("Marked {} stale package import job(s) as failed", timedOut)
            }
            val deleted = jobService.cleanupFinishedJobs(retention)
            if (deleted > 0) {
                logger.info("Deleted {} finished package import job(s)", deleted)
            }
        }
    }
}
