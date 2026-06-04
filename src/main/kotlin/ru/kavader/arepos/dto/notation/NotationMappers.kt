package ru.kavader.arepos.dto.notation

import org.springframework.stereotype.Component
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.RelationRuleListLightProjection
import ru.kavader.arepos.repository.RelationRuleListProjection
import ru.kavader.arepos.security.ResourceAccessService

@Component
class NotationMapper(
    private val accessService: ResourceAccessService
) {
    fun toResponse(notation: Notations, accessPermission: String?): NotationResponse = NotationResponse(
        id = requireNotNull(notation.id),
        name = notation.name,
        version = notation.version,
        ownerId = notation.owner.id!!,
        accessPermission = accessPermission,
        attrs = notation.attrs,
        createdAt = notation.createdAt,
        updatedAt = notation.updatedAt,
        sourceId = notation.source?.id
    )

    fun toResponse(notation: Notations): NotationResponse = NotationResponse(
        id = requireNotNull(notation.id),
        name = notation.name,
        version = notation.version,
        ownerId = notation.owner.id!!,
        accessPermission = accessService.notationAccessPermission(notation),
        attrs = notation.attrs,
        createdAt = notation.createdAt,
        updatedAt = notation.updatedAt,
        sourceId = notation.source?.id
    )

    fun toResponse(nodeType: NodeTypes, accessPermission: String?): NodeTypeResponse = NodeTypeResponse(
        id = requireNotNull(nodeType.id),
        name = nodeType.name,
        ownerId = nodeType.owner.id!!,
        accessPermission = accessPermission,
        attrs = nodeType.attrs,
        createdAt = nodeType.createdAt,
        updatedAt = nodeType.updatedAt
    )

    fun toResponse(component: Components): ComponentResponse = ComponentResponse(
        id = requireNotNull(component.id),
        name = component.name,
        version = component.version,
        notationId = component.notation.id!!,
        ownerId = component.owner.id!!,
        nodeTypeId = component.nodeType.id!!,
        attrs = component.attrs,
        createdAt = component.createdAt,
        updatedAt = component.updatedAt
    )

    fun toResponse(relation: Relations): RelationResponse = RelationResponse(
        id = requireNotNull(relation.id),
        name = relation.name,
        version = relation.version,
        notationId = relation.notation.id!!,
        ownerId = relation.owner.id!!,
        linkTypeId = relation.linkType.id!!,
        attrs = relation.attrs,
        createdAt = relation.createdAt,
        updatedAt = relation.updatedAt
    )

    fun toResponse(relationRule: RelationRules): RelationRuleResponse = RelationRuleResponse(
        id = requireNotNull(relationRule.id),
        relationId = relationRule.relation.id!!,
        fromComponentId = relationRule.fromComponent.id!!,
        toComponentId = relationRule.toComponent.id!!,
        ownerId = relationRule.owner.id!!,
        attrs = relationRule.attrs,
        createdAt = relationRule.createdAt,
        updatedAt = relationRule.updatedAt
    )

    fun toResponse(relationRule: RelationRuleListProjection, includeAttrs: Boolean): RelationRuleResponse =
        RelationRuleResponse(
            id = relationRule.id,
            relationId = relationRule.relationId,
            fromComponentId = relationRule.fromComponentId,
            toComponentId = relationRule.toComponentId,
            ownerId = relationRule.ownerId,
            attrs = if (includeAttrs) relationRule.attrs else null,
            createdAt = relationRule.createdAt,
            updatedAt = relationRule.updatedAt
        )

    fun toResponse(relationRule: RelationRuleListLightProjection): RelationRuleResponse = RelationRuleResponse(
        id = relationRule.id,
        relationId = relationRule.relationId,
        fromComponentId = relationRule.fromComponentId,
        toComponentId = relationRule.toComponentId,
        ownerId = relationRule.ownerId,
        attrs = null,
        createdAt = relationRule.createdAt,
        updatedAt = relationRule.updatedAt
    )

    fun toResponse(nodeType: NodeTypes): NodeTypeResponse = NodeTypeResponse(
        id = requireNotNull(nodeType.id),
        name = nodeType.name,
        ownerId = nodeType.owner.id!!,
        accessPermission = accessService.nodeTypeAccessPermission(nodeType),
        attrs = nodeType.attrs,
        createdAt = nodeType.createdAt,
        updatedAt = nodeType.updatedAt
    )

    fun toResponse(linkType: LinkTypes, accessPermission: String?): LinkTypeResponse = LinkTypeResponse(
        id = requireNotNull(linkType.id),
        name = linkType.name,
        ownerId = linkType.owner.id!!,
        accessPermission = accessPermission,
        attrs = linkType.attrs,
        createdAt = linkType.createdAt,
        updatedAt = linkType.updatedAt
    )

    fun toResponse(linkType: LinkTypes): LinkTypeResponse = LinkTypeResponse(
        id = requireNotNull(linkType.id),
        name = linkType.name,
        ownerId = linkType.owner.id!!,
        accessPermission = accessService.linkTypeAccessPermission(linkType),
        attrs = linkType.attrs,
        createdAt = linkType.createdAt,
        updatedAt = linkType.updatedAt
    )

    fun toResponse(nodeShape: NodeShapes, accessPermission: String?): NodeShapeResponse = NodeShapeResponse(
        id = requireNotNull(nodeShape.id),
        name = nodeShape.name,
        ownerId = nodeShape.owner.id!!,
        accessPermission = accessPermission,
        outline = nodeShape.outline,
        contentArea = nodeShape.contentArea,
        attrs = nodeShape.attrs,
        createdAt = nodeShape.createdAt,
        updatedAt = nodeShape.updatedAt
    )

    fun toResponse(nodeShape: NodeShapes): NodeShapeResponse = NodeShapeResponse(
        id = requireNotNull(nodeShape.id),
        name = nodeShape.name,
        ownerId = nodeShape.owner.id!!,
        accessPermission = accessService.nodeShapeAccessPermission(nodeShape),
        outline = nodeShape.outline,
        contentArea = nodeShape.contentArea,
        attrs = nodeShape.attrs,
        createdAt = nodeShape.createdAt,
        updatedAt = nodeShape.updatedAt
    )
}
