package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import ru.kavader.arepos.repository.DiagramsRepository
import java.time.Instant
import java.util.UUID

@Service
class DiagramCanvasInstancesCleanupService(
    private val diagramsRepository: DiagramsRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * Обновляет attrs всех неудалённых диаграмм модели: убирает ссылки на удалённые ноды и связи.
     * Вызывать после удаления строк nodes/links (в конце batch-save — после обновлений диаграмм).
     */
    fun removeDeletedModelEntitiesFromAllDiagrams(
        modelId: UUID,
        deletedNodeIds: Collection<UUID>,
        deletedLinkIds: Collection<UUID>,
        now: Instant
    ): Int {
        val nodeSet = deletedNodeIds.toSet()
        val linkSet = deletedLinkIds.toSet()
        if (nodeSet.isEmpty() && linkSet.isEmpty()) return 0

        val diagrams = diagramsRepository.findAllActiveByModelId(modelId)
        var updated = 0
        for (d in diagrams) {
            val cleaned = DiagramCanvasJsonCleanup.cleanupDiagramAttrs(
                d.attrs,
                objectMapper,
                nodeSet,
                linkSet
            )
            if (cleaned != d.attrs) {
                diagramsRepository.save(
                    d.copy(
                        attrs = cleaned,
                        updatedAt = now
                    )
                )
                updated++
            }
        }
        return updated
    }
}
