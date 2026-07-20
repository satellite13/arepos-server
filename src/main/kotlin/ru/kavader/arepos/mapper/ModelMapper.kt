package ru.kavader.arepos.mapper

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.model.DiagramResponse
import ru.kavader.arepos.dto.model.LinkResponse
import ru.kavader.arepos.dto.model.ModelResponse
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.security.ResourceAccessService

@Component
class ModelMapper(
    private val accessService: ResourceAccessService,
    private val userMapper: UserMapper
) {
    fun toResponse(model: Models, accessPermission: String?): ModelResponse = ModelResponse(
        id = requireNotNull(model.id),
        name = model.name,
        version = model.version,
        ownerId = requireNotNull(model.owner.id) { "Model owner ID must not be null" },
        ownerEmail = model.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(model.owner),
        accessPermission = accessPermission,
        attrs = model.attrs,
        createdAt = model.createdAt,
        updatedAt = model.updatedAt,
        sourceId = model.source?.id
    )

    fun toResponse(model: Models): ModelResponse = ModelResponse(
        id = requireNotNull(model.id),
        name = model.name,
        version = model.version,
        ownerId = requireNotNull(model.owner.id) { "Model owner ID must not be null" },
        ownerEmail = model.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(model.owner),
        accessPermission = accessService.modelAccessPermission(model),
        attrs = model.attrs,
        createdAt = model.createdAt,
        updatedAt = model.updatedAt,
        sourceId = model.source?.id
    )

    fun toResponse(diagram: Diagrams, includeAttrs: Boolean = true): DiagramResponse = DiagramResponse(
        id = requireNotNull(diagram.id),
        name = diagram.name,
        version = diagram.version,
        ownerId = requireNotNull(diagram.owner.id) { "Diagram owner ID must not be null" },
        modelId = requireNotNull(diagram.model.id) { "Diagram model ID must not be null" },
        nodeId = diagram.node?.id,
        notationId = requireNotNull(diagram.notation.id) { "Diagram notation ID must not be null" },
        attrs = if (includeAttrs) diagram.attrs else null,
        createdAt = diagram.createdAt,
        updatedAt = diagram.updatedAt
    )

    fun toResponse(node: Nodes): NodeResponse = NodeResponse(
        id = requireNotNull(node.id),
        stableId = node.stableId,
        name = node.name,
        modelId = requireNotNull(node.model.id) { "Node model ID must not be null" },
        ownerId = requireNotNull(node.owner.id) { "Node owner ID must not be null" },
        nodeTypeId = requireNotNull(node.nodeType.id) { "Node type ID must not be null" },
        parentNodeId = node.parentNode?.id,
        attrs = node.attrs,
        createdAt = node.createdAt,
        updatedAt = node.updatedAt
    )

    fun toResponse(link: Links): LinkResponse = LinkResponse(
        id = requireNotNull(link.id),
        stableId = link.stableId,
        sourceId = requireNotNull(link.source.id) { "Link source ID must not be null" },
        targetId = requireNotNull(link.target.id) { "Link target ID must not be null" },
        modelId = requireNotNull(link.model.id) { "Link model ID must not be null" },
        ownerId = requireNotNull(link.owner.id) { "Link owner ID must not be null" },
        linkTypeId = requireNotNull(link.linkType.id) { "Link type ID must not be null" },
        attrs = link.attrs,
        createdAt = link.createdAt,
        updatedAt = link.updatedAt
    )
}
