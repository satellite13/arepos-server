package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.NodesRepository
import java.util.UUID

/**
 * Untyped «Diagram only» nodes are hidden from the model tree. Deleting a diagram
 * must remove those nodes when they are no longer placed on any live diagram.
 */
@Service
class DiagramOnlyOrphanCleanupService(
    private val nodesRepository: NodesRepository,
    private val diagramsRepository: DiagramsRepository,
    private val objectMapper: ObjectMapper
) {
    fun deleteOrphansAfterDiagramsDeleted(modelId: UUID, deletedDiagrams: List<Diagrams>) {
        val candidates = deletedDiagrams
            .flatMap { DiagramCanvasJsonCleanup.extractModelNodeIds(it.attrs, objectMapper) }
            .toSet()
        if (candidates.isEmpty()) return

        val stillUsed = diagramsRepository.findAllActiveByModelId(modelId)
            .flatMap { DiagramCanvasJsonCleanup.extractModelNodeIds(it.attrs, objectMapper) }
            .toSet()
        val orphanIds = candidates - stillUsed
        for (nodeId in orphanIds) {
            val node = nodesRepository.findById(nodeId).orElse(null) ?: continue
            if (node.model.id != modelId) continue
            if (!node.nodeType.name.equals(DIAGRAM_ONLY_TYPE_NAME, ignoreCase = true)) continue
            nodesRepository.delete(node)
        }
    }

    companion object {
        const val DIAGRAM_ONLY_TYPE_NAME: String = "Diagram only"
    }
}
