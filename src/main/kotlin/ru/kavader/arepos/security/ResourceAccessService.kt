package ru.kavader.arepos.security

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
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
    fun currentUserId(): UUID = CurrentUser.getId()
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

    fun canEditModel(model: Models): Boolean = canEditTopLevel(model.owner.id!!, ShareResourceType.MODEL, model.id!!)

    fun canEditNotation(notation: Notations): Boolean =
        canEditTopLevel(notation.owner.id!!, ShareResourceType.NOTATION, notation.id!!)

    fun canEditNodeType(nodeType: NodeTypes): Boolean =
        canEditTopLevel(nodeType.owner.id!!, ShareResourceType.NODE_TYPE, nodeType.id!!)

    fun canEditLinkType(linkType: LinkTypes): Boolean =
        canEditTopLevel(linkType.owner.id!!, ShareResourceType.LINK_TYPE, linkType.id!!)

    fun canEditNode(node: Nodes): Boolean = canEditModel(node.model)

    fun canEditLink(link: Links): Boolean = canEditModel(link.model)

    fun canEditComponent(component: Components): Boolean = canEditNotation(component.notation)

    fun canEditRelation(relation: Relations): Boolean = canEditNotation(relation.notation)

    fun canEditRelationRule(relationRule: RelationRules): Boolean = canEditRelation(relationRule.relation)

    fun canEditDiagram(diagram: Diagrams): Boolean = canEditModel(diagram.model) && canEditNotation(diagram.notation)

    fun requireCanEditModel(model: Models) {
        if (!canEditModel(model)) {
            deny()
        }
    }

    fun requireCanEditNotation(notation: Notations) {
        if (!canEditNotation(notation)) {
            deny()
        }
    }

    fun requireCanEditNodeType(nodeType: NodeTypes) {
        if (!canEditNodeType(nodeType)) {
            deny()
        }
    }

    fun requireCanEditLinkType(linkType: LinkTypes) {
        if (!canEditLinkType(linkType)) {
            deny()
        }
    }

    fun requireCanEditNode(node: Nodes) {
        if (!canEditNode(node)) {
            deny()
        }
    }

    fun requireCanEditLink(link: Links) {
        if (!canEditLink(link)) {
            deny()
        }
    }

    fun requireCanEditComponent(component: Components) {
        if (!canEditComponent(component)) {
            deny()
        }
    }

    fun requireCanEditRelation(relation: Relations) {
        if (!canEditRelation(relation)) {
            deny()
        }
    }

    fun requireCanEditRelationRule(relationRule: RelationRules) {
        if (!canEditRelationRule(relationRule)) {
            deny()
        }
    }

    fun requireCanEditDiagram(diagram: Diagrams) {
        if (!canEditDiagram(diagram)) {
            deny()
        }
    }

    fun sharedResourceIds(resourceType: ShareResourceType): Set<UUID> {
        val userId = currentUserId()
        return resourceSharesRepository.findByGranteeUserIdAndPermission(userId, SharePermission.EDIT)
            .asSequence()
            .filter { it.resourceType == resourceType }
            .map { it.resourceId }
            .toSet()
    }

    fun hasDirectShare(resourceType: ShareResourceType, resourceId: UUID): Boolean {
        val userId = currentUserId()
        return resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            granteeUserId = userId,
            permission = SharePermission.EDIT
        )
    }

    fun canManageShares(ownerId: UUID): Boolean {
        val userId = CurrentUser.getId()
        return CurrentUser.isAdmin() || userId == ownerId
    }

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
        )
    }

    private fun deny(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
}
