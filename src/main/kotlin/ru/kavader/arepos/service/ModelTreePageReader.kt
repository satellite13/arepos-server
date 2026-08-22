package ru.kavader.arepos.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.repository.NodeTreePageProjection
import ru.kavader.arepos.repository.NodesRepository
import java.util.UUID

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class ModelTreePageReader(
    private val nodesRepository: NodesRepository
) {
    fun readPage(
        modelId: UUID,
        parentNodeId: UUID?,
        excludeSystem: Boolean,
        foldersOnly: Boolean,
        pageable: Pageable
    ): Page<NodeResponse> {
        val projections = nodesRepository.findDirectChildrenPage(
            modelId = modelId,
            parentNodeId = parentNodeId,
            excludeSystem = excludeSystem,
            foldersOnly = foldersOnly,
            pageable = pageable
        )
        return PageImpl(
            projections.content.map(NodeTreePageProjection::toResponse),
            pageable,
            projections.totalElements
        )
    }
}

private fun NodeTreePageProjection.toResponse() = NodeResponse(
    id = getId(),
    stableId = getStableId(),
    name = getName(),
    modelId = getModelId(),
    ownerId = getOwnerId(),
    nodeTypeId = getNodeTypeId(),
    parentNodeId = getParentNodeId(),
    attrs = getAttrs(),
    createdAt = getCreatedAt(),
    updatedAt = getUpdatedAt(),
    hasChildren = getHasChildren()
)
