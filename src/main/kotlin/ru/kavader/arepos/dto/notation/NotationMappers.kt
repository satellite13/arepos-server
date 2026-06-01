package ru.kavader.arepos.dto.notation

import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.RelationRuleListLightProjection
import ru.kavader.arepos.repository.RelationRuleListProjection
import ru.kavader.arepos.security.ResourceAccessService

fun Notations.toResponse(accessService: ResourceAccessService): NotationResponse = NotationResponse(
    id = requireNotNull(id),
    name = name,
    version = version,
    ownerId = owner.id!!,
    accessPermission = accessService.notationAccessPermission(this),
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceId = source?.id
)

fun Components.toResponse(accessService: ResourceAccessService): ComponentResponse = ComponentResponse(
    id = requireNotNull(id),
    name = name,
    version = version,
    notationId = notation.id!!,
    ownerId = owner.id!!,
    nodeTypeId = nodeType.id!!,
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Relations.toResponse(accessService: ResourceAccessService): RelationResponse = RelationResponse(
    id = requireNotNull(id),
    name = name,
    version = version,
    notationId = notation.id!!,
    ownerId = owner.id!!,
    linkTypeId = linkType.id!!,
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RelationRules.toResponse(accessService: ResourceAccessService): RelationRuleResponse = RelationRuleResponse(
    id = requireNotNull(id),
    relationId = relation.id!!,
    fromComponentId = fromComponent.id!!,
    toComponentId = toComponent.id!!,
    ownerId = owner.id!!,
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RelationRuleListProjection.toResponse(includeAttrs: Boolean): RelationRuleResponse = RelationRuleResponse(
    id = id,
    relationId = relationId,
    fromComponentId = fromComponentId,
    toComponentId = toComponentId,
    ownerId = ownerId,
    attrs = if (includeAttrs) attrs else null,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RelationRuleListLightProjection.toResponse(): RelationRuleResponse = RelationRuleResponse(
    id = id,
    relationId = relationId,
    fromComponentId = fromComponentId,
    toComponentId = toComponentId,
    ownerId = ownerId,
    attrs = null,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun NodeTypes.toResponse(accessService: ResourceAccessService): NodeTypeResponse = NodeTypeResponse(
    id = requireNotNull(id),
    name = name,
    ownerId = owner.id!!,
    accessPermission = accessService.nodeTypeAccessPermission(this),
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun LinkTypes.toResponse(accessService: ResourceAccessService): LinkTypeResponse = LinkTypeResponse(
    id = requireNotNull(id),
    name = name,
    ownerId = owner.id!!,
    accessPermission = accessService.linkTypeAccessPermission(this),
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun NodeShapes.toResponse(accessService: ResourceAccessService): NodeShapeResponse = NodeShapeResponse(
    id = requireNotNull(id),
    name = name,
    ownerId = owner.id!!,
    outline = outline,
    contentArea = contentArea,
    attrs = attrs,
    createdAt = createdAt,
    updatedAt = updatedAt,
    canEdit = accessService.canEditNodeShape(this)
)
