package ru.kavader.arepos.mapper

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.notation.ComponentResponse
import ru.kavader.arepos.dto.notation.LinkTypeResponse
import ru.kavader.arepos.dto.notation.NodeShapeResponse
import ru.kavader.arepos.dto.notation.NodeTypeResponse
import ru.kavader.arepos.dto.notation.NotationMetaResponse
import ru.kavader.arepos.dto.notation.NotationResponse
import ru.kavader.arepos.dto.notation.RelationResponse
import ru.kavader.arepos.dto.notation.RelationRuleResponse
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.RelationRuleListLightProjection
import ru.kavader.arepos.repository.RelationRuleListProjection
import ru.kavader.arepos.security.ResourceAccessService

@Component
class NotationMapper(
    private val accessService: ResourceAccessService,
    private val userMapper: UserMapper
) {
    fun toResponse(notation: Notations, accessPermission: String?): NotationResponse = NotationResponse(
        id = requireNotNull(notation.id),
        name = notation.name,
        version = notation.version,
        ownerId = requireNotNull(notation.owner.id) { "Notation owner ID must not be null" },
        ownerEmail = notation.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(notation.owner),
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
        ownerId = requireNotNull(notation.owner.id) { "Notation owner ID must not be null" },
        ownerEmail = notation.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(notation.owner),
        accessPermission = accessService.notationAccessPermission(notation),
        attrs = notation.attrs,
        createdAt = notation.createdAt,
        updatedAt = notation.updatedAt,
        sourceId = notation.source?.id
    )

    fun toMetaResponse(notation: Notations): NotationMetaResponse = NotationMetaResponse(
        id = requireNotNull(notation.id),
        name = notation.name,
        version = notation.version,
        ownerId = requireNotNull(notation.owner.id) { "Notation owner ID must not be null" },
        ownerEmail = notation.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(notation.owner)
    )

    fun toResponse(nodeType: NodeTypes, accessPermission: String?): NodeTypeResponse = NodeTypeResponse(
        id = requireNotNull(nodeType.id),
        name = nodeType.name,
        ownerId = requireNotNull(nodeType.owner.id) { "Node type owner ID must not be null" },
        ownerEmail = nodeType.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(nodeType.owner),
        accessPermission = accessPermission,
        attrs = nodeType.attrs,
        createdAt = nodeType.createdAt,
        updatedAt = nodeType.updatedAt
    )

    fun toResponse(component: Components): ComponentResponse = ComponentResponse(
        id = requireNotNull(component.id),
        name = component.name,
        version = component.version,
        notationId = requireNotNull(component.notation.id) { "Component notation ID must not be null" },
        ownerId = requireNotNull(component.owner.id) { "Component owner ID must not be null" },
        nodeTypeId = requireNotNull(component.nodeType.id) { "Component node type ID must not be null" },
        attrs = component.attrs,
        createdAt = component.createdAt,
        updatedAt = component.updatedAt
    )

    fun toResponse(relation: Relations): RelationResponse = RelationResponse(
        id = requireNotNull(relation.id),
        name = relation.name,
        version = relation.version,
        notationId = requireNotNull(relation.notation.id) { "Relation notation ID must not be null" },
        ownerId = requireNotNull(relation.owner.id) { "Relation owner ID must not be null" },
        linkTypeId = requireNotNull(relation.linkType.id) { "Relation link type ID must not be null" },
        attrs = relation.attrs,
        createdAt = relation.createdAt,
        updatedAt = relation.updatedAt
    )

    fun toResponse(relationRule: RelationRules): RelationRuleResponse = RelationRuleResponse(
        id = requireNotNull(relationRule.id),
        relationId = requireNotNull(relationRule.relation.id) { "Relation rule relation ID must not be null" },
        fromComponentId = requireNotNull(relationRule.fromComponent.id) {
            "Relation rule source component ID must not be null"
        },
        toComponentId = requireNotNull(relationRule.toComponent.id) {
            "Relation rule target component ID must not be null"
        },
        ownerId = requireNotNull(relationRule.owner.id) { "Relation rule owner ID must not be null" },
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
        ownerId = requireNotNull(nodeType.owner.id) { "Node type owner ID must not be null" },
        ownerEmail = nodeType.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(nodeType.owner),
        accessPermission = accessService.nodeTypeAccessPermission(nodeType),
        attrs = nodeType.attrs,
        createdAt = nodeType.createdAt,
        updatedAt = nodeType.updatedAt
    )

    fun toResponse(linkType: LinkTypes, accessPermission: String?): LinkTypeResponse = LinkTypeResponse(
        id = requireNotNull(linkType.id),
        name = linkType.name,
        ownerId = requireNotNull(linkType.owner.id) { "Link type owner ID must not be null" },
        ownerEmail = linkType.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(linkType.owner),
        accessPermission = accessPermission,
        attrs = linkType.attrs,
        createdAt = linkType.createdAt,
        updatedAt = linkType.updatedAt
    )

    fun toResponse(linkType: LinkTypes): LinkTypeResponse = LinkTypeResponse(
        id = requireNotNull(linkType.id),
        name = linkType.name,
        ownerId = requireNotNull(linkType.owner.id) { "Link type owner ID must not be null" },
        ownerEmail = linkType.owner.email,
        ownerDisplayName = userMapper.ownerDisplayName(linkType.owner),
        accessPermission = accessService.linkTypeAccessPermission(linkType),
        attrs = linkType.attrs,
        createdAt = linkType.createdAt,
        updatedAt = linkType.updatedAt
    )

    fun toResponse(nodeShape: NodeShapes, accessPermission: String?): NodeShapeResponse = NodeShapeResponse(
        id = requireNotNull(nodeShape.id),
        name = nodeShape.name,
        ownerId = requireNotNull(nodeShape.owner.id) { "Node shape owner ID must not be null" },
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
        ownerId = requireNotNull(nodeShape.owner.id) { "Node shape owner ID must not be null" },
        accessPermission = accessService.nodeShapeAccessPermission(nodeShape),
        outline = nodeShape.outline,
        contentArea = nodeShape.contentArea,
        attrs = nodeShape.attrs,
        createdAt = nodeShape.createdAt,
        updatedAt = nodeShape.updatedAt
    )
}
