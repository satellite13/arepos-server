package ru.kavader.arepos.dto.model

import org.springframework.stereotype.Component
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.security.ResourceAccessService

@Component
class ModelMapper(
    private val accessService: ResourceAccessService
) {
    fun toResponse(model: Models): ModelResponse = ModelResponse(
        id = requireNotNull(model.id),
        name = model.name,
        version = model.version,
        ownerId = model.owner.id!!,
        accessPermission = accessService.modelAccessPermission(model),
        attrs = model.attrs,
        createdAt = model.createdAt,
        updatedAt = model.updatedAt,
        sourceId = model.source?.id
    )

    fun toResponse(diagram: Diagrams): DiagramResponse = DiagramResponse(
        id = requireNotNull(diagram.id),
        name = diagram.name,
        version = diagram.version,
        ownerId = diagram.owner.id!!,
        modelId = diagram.model.id!!,
        nodeId = diagram.node?.id,
        notationId = diagram.notation.id!!,
        attrs = diagram.attrs,
        createdAt = diagram.createdAt,
        updatedAt = diagram.updatedAt
    )

    fun toResponse(node: Nodes): NodeResponse = NodeResponse(
        id = requireNotNull(node.id),
        stableId = node.stableId,
        name = node.name,
        modelId = node.model.id!!,
        ownerId = node.owner.id!!,
        nodeTypeId = node.nodeType.id!!,
        parentNodeId = node.parentNode?.id,
        attrs = node.attrs,
        createdAt = node.createdAt,
        updatedAt = node.updatedAt
    )

    fun toResponse(link: Links): LinkResponse = LinkResponse(
        id = requireNotNull(link.id),
        stableId = link.stableId,
        sourceId = link.source.id!!,
        targetId = link.target.id!!,
        modelId = link.model.id!!,
        ownerId = link.owner.id!!,
        linkTypeId = link.linkType.id!!,
        attrs = link.attrs,
        createdAt = link.createdAt,
        updatedAt = link.updatedAt
    )
}
