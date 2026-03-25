package ru.kavader.arepos.config

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.kavader.arepos.service.DiagramCollaborationService

@Component
class DiagramSpectatorCleanupScheduler(
    private val diagramCollaborationService: DiagramCollaborationService
) {
    @Scheduled(fixedDelayString = "\${arepos.diagram-spectator.cleanup-ms:15000}")
    fun purgeStaleSpectators() {
        diagramCollaborationService.purgeStaleSpectators()
    }
}
