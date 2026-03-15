package ru.kavader.arepos.security

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.ResourceSharesRepository
import java.util.UUID

@Service
class ResourceAccessService(
    private val resourceSharesRepository: ResourceSharesRepository
) {
    private val viewPermissions = setOf(SharePermission.VIEW, SharePermission.EDIT)

    fun currentUserId(): UUID = CurrentUser.getId()
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

    fun canEditModel(model: Models): Boolean = canEditTopLevel(model.owner.id!!, ShareResourceType.MODEL, model.id!!)

    fun canViewModel(model: Models): Boolean = canViewTopLevel(model.owner.id!!, ShareResourceType.MODEL, model.id!!)

    fun canEditNotation(notation: Notations): Boolean =
        canEditTopLevel(notation.owner.id!!, ShareResourceType.NOTATION, notation.id!!)

    fun canViewNotation(notation: Notations): Boolean =
        canViewTopLevel(notation.owner.id!!, ShareResourceType.NOTATION, notation.id!!)

    fun canEditNodeType(nodeType: NodeTypes): Boolean =
        canEditTopLevel(nodeType.owner.id!!, ShareResourceType.NODE_TYPE, nodeType.id!!)

    fun canViewNodeType(nodeType: NodeTypes): Boolean =
        canViewTopLevel(nodeType.owner.id!!, ShareResourceType.NODE_TYPE, nodeType.id!!)

    fun canEditLinkType(linkType: LinkTypes): Boolean =
        canEditTopLevel(linkType.owner.id!!, ShareResourceType.LINK_TYPE, linkType.id!!)

    fun canViewLinkType(linkType: LinkTypes): Boolean =
        canViewTopLevel(linkType.owner.id!!, ShareResourceType.LINK_TYPE, linkType.id!!)

    fun canEditNodeShape(shape: NodeShapes): Boolean =
        canEditTopLevel(shape.owner.id!!, ShareResourceType.NODE_SHAPE, shape.id!!)

    fun canUseNodeType(nodeType: NodeTypes): Boolean = canViewNodeType(nodeType) || isCommonType(nodeType.owner)

    fun canUseLinkType(linkType: LinkTypes): Boolean = canViewLinkType(linkType) || isCommonType(linkType.owner)

    fun canEditNode(node: Nodes): Boolean = canEditModel(node.model)

    fun canViewNode(node: Nodes): Boolean = canViewModel(node.model)

    fun canEditLink(link: Links): Boolean = canEditModel(link.model)

    fun canViewLink(link: Links): Boolean = canViewModel(link.model)

    fun canEditComponent(component: Components): Boolean = canEditNotation(component.notation)

    fun canViewComponent(component: Components): Boolean = canViewNotation(component.notation)

    fun canEditRelation(relation: Relations): Boolean = canEditNotation(relation.notation)

    fun canViewRelation(relation: Relations): Boolean = canViewNotation(relation.notation)

    fun canEditRelationRule(relationRule: RelationRules): Boolean = canEditRelation(relationRule.relation)

    fun canViewRelationRule(relationRule: RelationRules): Boolean = canViewRelation(relationRule.relation)

    fun canEditDiagram(diagram: Diagrams): Boolean = canEditModel(diagram.model)

    fun canViewDiagram(diagram: Diagrams): Boolean =
        canViewModel(diagram.model) && (canViewNotation(diagram.notation) || canEditModel(diagram.model))

    fun canViewFile(file: Files): Boolean =
        CurrentUser.isAdmin() || file.owner.id == CurrentUser.getId()

    fun requireCanViewFile(file: Files) {
        if (!canViewFile(file)) {
            deny()
        }
    }

    fun requireCanEditModel(model: Models) {
        if (!canEditModel(model)) {
            deny()
        }
    }

    fun requireCanViewModel(model: Models) {
        if (!canViewModel(model)) {
            deny()
        }
    }

    fun requireCanEditNotation(notation: Notations) {
        if (!canEditNotation(notation)) {
            deny()
        }
    }

    fun requireCanViewNotation(notation: Notations) {
        if (!canViewNotation(notation)) {
            deny()
        }
    }

    fun requireCanEditNodeType(nodeType: NodeTypes) {
        if (!canEditNodeType(nodeType)) {
            deny()
        }
    }

    fun requireCanViewNodeType(nodeType: NodeTypes) {
        if (!canViewNodeType(nodeType)) {
            deny()
        }
    }

    fun requireCanEditLinkType(linkType: LinkTypes) {
        if (!canEditLinkType(linkType)) {
            deny()
        }
    }

    fun requireCanEditNodeShape(shape: NodeShapes) {
        if (!canEditNodeShape(shape)) {
            deny()
        }
    }

    fun requireCanViewLinkType(linkType: LinkTypes) {
        if (!canViewLinkType(linkType)) {
            deny()
        }
    }

    fun requireCanUseNodeType(nodeType: NodeTypes) {
        if (!canUseNodeType(nodeType)) {
            deny()
        }
    }

    fun requireCanUseLinkType(linkType: LinkTypes) {
        if (!canUseLinkType(linkType)) {
            deny()
        }
    }

    fun requireCanEditNode(node: Nodes) {
        if (!canEditNode(node)) {
            deny()
        }
    }

    fun requireCanViewNode(node: Nodes) {
        if (!canViewNode(node)) {
            deny()
        }
    }

    fun requireCanEditLink(link: Links) {
        if (!canEditLink(link)) {
            deny()
        }
    }

    fun requireCanViewLink(link: Links) {
        if (!canViewLink(link)) {
            deny()
        }
    }

    fun requireCanEditComponent(component: Components) {
        if (!canEditComponent(component)) {
            deny()
        }
    }

    fun requireCanViewComponent(component: Components) {
        if (!canViewComponent(component)) {
            deny()
        }
    }

    fun requireCanEditRelation(relation: Relations) {
        if (!canEditRelation(relation)) {
            deny()
        }
    }

    fun requireCanViewRelation(relation: Relations) {
        if (!canViewRelation(relation)) {
            deny()
        }
    }

    fun requireCanEditRelationRule(relationRule: RelationRules) {
        if (!canEditRelationRule(relationRule)) {
            deny()
        }
    }

    fun requireCanViewRelationRule(relationRule: RelationRules) {
        if (!canViewRelationRule(relationRule)) {
            deny()
        }
    }

    fun requireCanEditDiagram(diagram: Diagrams) {
        if (!canEditDiagram(diagram)) {
            deny()
        }
    }

    fun requireCanViewDiagram(diagram: Diagrams) {
        if (!canViewDiagram(diagram)) {
            deny()
        }
    }

    fun sharedResourceIds(resourceType: ShareResourceType): Set<UUID> {
        val userId = currentUserId()
        return (
            resourceSharesRepository.findByGranteeUserIdAndPermissionIn(userId, viewPermissions) +
                resourceSharesRepository.findByGranteeUserIsNullAndPermissionIn(viewPermissions)
            )
            .asSequence()
            .filter { it.resourceType == resourceType }
            .map { it.resourceId }
            .toSet()
    }

    fun hasDirectShare(resourceType: ShareResourceType, resourceId: UUID): Boolean {
        val userId = currentUserId()
        return resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermissionIn(
            resourceType = resourceType,
            resourceId = resourceId,
            granteeUserId = userId,
            permissions = viewPermissions
        ) || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermissionIn(
            resourceType = resourceType,
            resourceId = resourceId,
            permissions = viewPermissions
        )
    }

    fun canManageShares(ownerId: UUID): Boolean {
        val userId = CurrentUser.getId()
        return CurrentUser.isAdmin() || userId == ownerId
    }

    fun modelAccessPermission(model: Models): String? =
        topLevelAccessPermission(model.owner.id!!, ShareResourceType.MODEL, model.id!!)

    fun notationAccessPermission(notation: Notations): String? =
        topLevelAccessPermission(notation.owner.id!!, ShareResourceType.NOTATION, notation.id!!)

    fun nodeTypeAccessPermission(nodeType: NodeTypes): String? =
        topLevelAccessPermission(nodeType.owner.id!!, ShareResourceType.NODE_TYPE, nodeType.id!!)

    fun linkTypeAccessPermission(linkType: LinkTypes): String? =
        topLevelAccessPermission(linkType.owner.id!!, ShareResourceType.LINK_TYPE, linkType.id!!)

    fun nodeShapeAccessPermission(shape: NodeShapes): String? =
        topLevelAccessPermission(shape.owner.id!!, ShareResourceType.NODE_SHAPE, shape.id!!)

    private fun canEditTopLevel(ownerId: UUID, resourceType: ShareResourceType, resourceId: UUID): Boolean {
        if (CurrentUser.isAdmin()) {
            return true
        }
        val userId = CurrentUser.getId() ?: return false
        if (ownerId == userId) {
            return true
        }
        return resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            granteeUserId = userId,
            permission = SharePermission.EDIT
        ) || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            permission = SharePermission.EDIT
        )
    }

    private fun canViewTopLevel(ownerId: UUID, resourceType: ShareResourceType, resourceId: UUID): Boolean {
        if (CurrentUser.isAdmin()) {
            return true
        }
        val userId = CurrentUser.getId() ?: return false
        if (ownerId == userId) {
            return true
        }
        return resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermissionIn(
            resourceType = resourceType,
            resourceId = resourceId,
            granteeUserId = userId,
            permissions = viewPermissions
        ) || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermissionIn(
            resourceType = resourceType,
            resourceId = resourceId,
            permissions = viewPermissions
        )
    }

    private fun topLevelAccessPermission(ownerId: UUID, resourceType: ShareResourceType, resourceId: UUID): String? {
        if (CurrentUser.isAdmin()) {
            return "ADMIN"
        }
        val userId = CurrentUser.getId() ?: return null
        if (ownerId == userId) {
            return "OWNER"
        }
        if (
            resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermission(
                resourceType = resourceType,
                resourceId = resourceId,
                granteeUserId = userId,
                permission = SharePermission.EDIT
            )
                || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermission(
                resourceType = resourceType,
                resourceId = resourceId,
                permission = SharePermission.EDIT
            )
        ) {
            return "EDIT"
        }
        if (
            resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermissionIn(
                resourceType = resourceType,
                resourceId = resourceId,
                granteeUserId = userId,
                permissions = viewPermissions
            )
                || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermissionIn(
                resourceType = resourceType,
                resourceId = resourceId,
                permissions = viewPermissions
            )
        ) {
            return "VIEW"
        }
        return null
    }

    private fun isCommonType(owner: ru.kavader.arepos.model.Users): Boolean {
        return owner.email.equals("system@arepos.local", ignoreCase = true) || !owner.isActive
    }

    private fun deny(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
}
