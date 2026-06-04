package ru.kavader.arepos.service

import org.springframework.stereotype.Service
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.RelationsRepository
import java.util.*

/**
 * Shared helper for checking whether a node type or link type is reachable
 * through visible diagram notations within a model. Used by both regular
 * controllers and [ModelBatchSaveController].
 */
@Service
class ModelDiagramTypeValidator(
    private val diagramsRepository: DiagramsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository
) {

    fun isNodeTypeUsedInModelDiagramNotations(nodeTypeId: UUID, modelId: UUID): Boolean {
        val notationIds = diagramsRepository.findDistinctNotationIdsByModelId(modelId).toSet()
        if (notationIds.isEmpty()) return false
        return componentsRepository.existsByNodeTypeIdAndNotationIdIn(nodeTypeId, notationIds)
    }

    fun isLinkTypeUsedInModelDiagramNotations(linkTypeId: UUID, modelId: UUID): Boolean {
        val notationIds = diagramsRepository.findDistinctNotationIdsByModelId(modelId).toSet()
        if (notationIds.isEmpty()) return false
        return relationsRepository.existsByLinkTypeIdAndNotationIdIn(linkTypeId, notationIds)
    }
}
