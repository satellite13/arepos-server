package ru.kavader.arepos.config

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.kavader.arepos.service.DiagramCollaborationService
import java.util.concurrent.atomic.AtomicBoolean

@Component
class DiagramSpectatorCleanupScheduler(
    private val diagramCollaborationService: DiagramCollaborationService
) {
    private val running = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${arepos.diagram-spectator.cleanup-ms:15000}")
    @SchedulerLock(name = "DiagramSpectatorCleanupScheduler.purgeStaleSpectators", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    fun purgeStaleSpectators() {
        MdcRequestId.withGeneratedIfMissing("diagram-spectator-cleanup") {
            // Avoid overlapping runs in one JVM when cleanup takes longer than delay.
            if (!running.compareAndSet(false, true)) {
                return@withGeneratedIfMissing
            }
            try {
                diagramCollaborationService.purgeStaleSpectators()
            } finally {
                running.set(false)
            }
        }
    }
}
