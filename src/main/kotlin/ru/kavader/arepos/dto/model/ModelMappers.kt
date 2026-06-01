package ru.kavader.arepos.dto.model

import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.security.ResourceAccessService

fun Models.toResponse(accessService: ResourceAccessService): ModelResponse = ModelResponse(
    id = requireNotNull(id),
    name = name,
    version = version,
    ownerId = owner.id!!,
    accessPermission = accessService.modelAccessPermission(this),
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceId = source?.id
)

fun Diagrams.toResponse(accessService: ResourceAccessService): DiagramResponse = DiagramResponse(
    id = requireNotNull(id),
    name = name,
    version = version,
    ownerId = owner.id!!,
    modelId = model.id!!,
    nodeId = node?.id,
    notationId = notation.id!!,
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Nodes.toResponse(accessService: ResourceAccessService): NodeResponse = NodeResponse(
    id = requireNotNull(id),
    stableId = stableId,
    name = name,
    modelId = model.id!!,
    ownerId = owner.id!!,
    nodeTypeId = nodeType.id!!,
    parentNodeId = parentNode?.id,
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Links.toResponse(accessService: ResourceAccessService): LinkResponse = LinkResponse(
    id = requireNotNull(id),
    stableId = stableId,
    sourceId = source.id!!,
    targetId = target.id!!,
    modelId = model.id!!,
    ownerId = owner.id!!,
    linkTypeId = linkType.id!!,
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt
)
