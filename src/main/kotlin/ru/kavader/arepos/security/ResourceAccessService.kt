package ru.kavader.arepos.security

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.config.CerbosMode
import ru.kavader.arepos.config.CerbosProperties
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
    private val resourceSharesRepository: ResourceSharesRepository,
    private val cerbosProperties: CerbosProperties,
    private val cerbosDecisionService: CerbosDecisionService,
    private val authzObservabilityService: AuthzObservabilityService
) {
    companion object {
        private val log = LoggerFactory.getLogger(ResourceAccessService::class.java)
    }

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

    fun canViewFile(file: Files): Boolean {
        val legacyDecision = CurrentUser.isAdmin() || file.owner.id == CurrentUser.getId()
        return applyCerbosDecision(
            resourceKind = "file",
            action = "view",
            resourceId = file.id!!,
            ownerId = file.owner.id,
            legacyDecision = legacyDecision
        )
    }

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
        val legacyDecision = CurrentUser.isAdmin() || userId == ownerId
        // AccessShares is owner/admin controlled; use owner UUID as resource id.
        return applyCerbosDecision(
            resourceKind = "share",
            action = "manage",
            resourceId = ownerId,
            ownerId = ownerId,
            legacyDecision = legacyDecision
        )
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
        val legacyDecision = if (CurrentUser.isAdmin()) {
            true
        } else {
            val userId = CurrentUser.getId() ?: return false
            if (ownerId == userId) {
                true
            } else {
                resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermission(
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
        }

        return applyCerbosDecision(
            resourceKind = resourceType.name.lowercase(),
            action = "edit",
            resourceId = resourceId,
            ownerId = ownerId,
            legacyDecision = legacyDecision
        )
    }

    private fun canViewTopLevel(ownerId: UUID, resourceType: ShareResourceType, resourceId: UUID): Boolean {
        val legacyDecision = if (CurrentUser.isAdmin()) {
            true
        } else {
            val userId = CurrentUser.getId() ?: return false
            if (ownerId == userId) {
                true
            } else {
                resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermissionIn(
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
        }

        return applyCerbosDecision(
            resourceKind = resourceType.name.lowercase(),
            action = "view",
            resourceId = resourceId,
            ownerId = ownerId,
            legacyDecision = legacyDecision
        )
    }

    private fun applyCerbosDecision(
        resourceKind: String,
        action: String,
        resourceId: UUID,
        ownerId: UUID?,
        legacyDecision: Boolean
    ): Boolean {
        authzObservabilityService.recordLegacyDecision(resourceKind, action, legacyDecision)

        if (!cerbosProperties.enabled || cerbosProperties.mode == CerbosMode.DISABLED) {
            authzObservabilityService.recordFinalDecision(resourceKind, action, "legacy_disabled", legacyDecision)
            return legacyDecision
        }

        val startNanos = System.nanoTime()
        val cerbosDecision = try {
            cerbosDecisionService.check(
                CerbosAccessRequest(
                    resourceKind = resourceKind,
                    action = action,
                    resourceId = resourceId,
                    ownerId = ownerId
                )
            )
        } catch (ex: Exception) {
            authzObservabilityService.recordCerbosRequest(resourceKind, action, "error", System.nanoTime() - startNanos)
            if (cerbosProperties.failOpen) {
                log.warn("Cerbos check failed, fail-open active. resourceKind={}, action={}, resourceId={}", resourceKind, action, resourceId, ex)
                authzObservabilityService.recordFinalDecision(resourceKind, action, "legacy_fail_open", legacyDecision)
                return legacyDecision
            }
            log.warn("Cerbos check failed, fail-open disabled. Falling back to legacy decision to preserve current behavior. resourceKind={}, action={}, resourceId={}", resourceKind, action, resourceId, ex)
            authzObservabilityService.recordFinalDecision(resourceKind, action, "legacy_fail_closed_fallback", legacyDecision)
            return legacyDecision
        }

        authzObservabilityService.recordCerbosRequest(
            resourceKind = resourceKind,
            action = action,
            outcome = if (cerbosDecision == null) "not_implemented" else "ok",
            durationNanos = System.nanoTime() - startNanos
        )

        if (cerbosDecision == null) {
            authzObservabilityService.recordFinalDecision(resourceKind, action, "legacy_no_cerbos_decision", legacyDecision)
            return legacyDecision
        }

        if (cerbosProperties.shadowEnabled || cerbosProperties.mode == CerbosMode.SHADOW) {
            authzObservabilityService.recordShadowComparison(resourceKind, action, legacyDecision, cerbosDecision)
            if (legacyDecision != cerbosDecision) {
                log.debug(
                    "Cerbos shadow mismatch: resourceKind={}, action={}, resourceId={}, legacy={}, cerbos={}",
                    resourceKind,
                    action,
                    resourceId,
                    legacyDecision,
                    cerbosDecision
                )
            }
            authzObservabilityService.recordFinalDecision(resourceKind, action, "legacy_shadow", legacyDecision)
            return legacyDecision
        }

        if (cerbosProperties.enforceEnabled || cerbosProperties.mode == CerbosMode.ENFORCE) {
            if (legacyDecision != cerbosDecision) {
                log.info(
                    "Cerbos enforce override: resourceKind={}, action={}, resourceId={}, legacy={}, cerbos={}",
                    resourceKind,
                    action,
                    resourceId,
                    legacyDecision,
                    cerbosDecision
                )
            }
            authzObservabilityService.recordFinalDecision(resourceKind, action, "cerbos_enforce", cerbosDecision)
            return cerbosDecision
        }

        authzObservabilityService.recordFinalDecision(resourceKind, action, "legacy_mode_fallback", legacyDecision)
        return legacyDecision
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
