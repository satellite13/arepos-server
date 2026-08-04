package ru.kavader.arepos.security

import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.model.ValidationScripts
import ru.kavader.arepos.security.access.BatchEvaluator
import ru.kavader.arepos.security.access.NotationDiagramAccess
import ru.kavader.arepos.security.access.TopLevelAccess
import java.util.UUID

@Service
class ResourceAccessService(
    private val topLevelAccess: TopLevelAccess,
    private val notationDiagramAccess: NotationDiagramAccess,
    private val batchEvaluator: BatchEvaluator,
    @Lazy private val ownerResolutionService: OwnerResolutionService
) {
    fun currentUserId(): UUID = ownerResolutionService.currentUserId()

    fun canEditModel(model: Models): Boolean = topLevelAccess.canEditModel(model)

    fun canViewModel(model: Models): Boolean = topLevelAccess.canViewModel(model)

    fun canViewModels(models: Collection<Models>): Map<UUID, Boolean> =
        topLevelAccess.canViewModels(models)

    fun canViewNotations(notations: Collection<Notations>): Map<UUID, Boolean> =
        topLevelAccess.canViewNotationsDirect(notations)

    fun filterViewableModels(models: Collection<Models>): List<Models> =
        topLevelAccess.filterViewableModels(models).filter { isMcpModelAllowed(it.id) }

    fun filterViewableNotations(notations: Collection<Notations>): List<Notations> =
        notationDiagramAccess.filterViewableNotations(notations)

    fun canViewDiagrams(diagrams: Collection<Diagrams>): Map<UUID, Boolean> =
        notationDiagramAccess.canViewDiagrams(diagrams)

    fun filterViewableDiagrams(diagrams: Collection<Diagrams>): List<Diagrams> =
        notationDiagramAccess.filterViewableDiagrams(diagrams)

    fun canEditNotation(notation: Notations): Boolean = topLevelAccess.canEditNotation(notation)

    fun canViewNotation(notation: Notations): Boolean = notationDiagramAccess.canViewNotation(notation)

    fun canEditNodeType(nodeType: NodeTypes): Boolean = topLevelAccess.canEditNodeType(nodeType)

    fun canViewNodeType(nodeType: NodeTypes): Boolean = notationDiagramAccess.canViewNodeType(nodeType)

    fun canEditLinkType(linkType: LinkTypes): Boolean = topLevelAccess.canEditLinkType(linkType)

    fun canViewLinkType(linkType: LinkTypes): Boolean = notationDiagramAccess.canViewLinkType(linkType)

    fun canEditNodeShape(shape: NodeShapes): Boolean = topLevelAccess.canEditNodeShape(shape)

    fun canViewNodeShape(shape: NodeShapes): Boolean = topLevelAccess.canViewNodeShape(shape)

    fun canViewNodeShapes(shapes: Collection<NodeShapes>): Map<UUID, Boolean> =
        topLevelAccess.canViewNodeShapes(shapes)

    fun filterViewableNodeShapes(shapes: Collection<NodeShapes>): List<NodeShapes> =
        topLevelAccess.filterViewableNodeShapes(shapes)

    fun canEditValidationScript(script: ValidationScripts): Boolean =
        topLevelAccess.canEditValidationScript(script)

    fun canViewValidationScript(script: ValidationScripts): Boolean =
        topLevelAccess.canViewValidationScript(script)

    fun canViewValidationScripts(scripts: Collection<ValidationScripts>): Map<UUID, Boolean> =
        topLevelAccess.canViewValidationScripts(scripts)

    fun filterViewableValidationScripts(scripts: Collection<ValidationScripts>): List<ValidationScripts> =
        topLevelAccess.filterViewableValidationScripts(scripts)

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

    fun canViewDiagram(diagram: Diagrams): Boolean = canViewModel(diagram.model)

    fun canViewFile(file: Files): Boolean =
        batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.FILE,
            action = CerbosAction.VIEW,
            resourceId = file.id!!,
            ownerId = file.owner.id
        )

    fun requireCanViewFile(file: Files) = requireAllowed(canViewFile(file))

    fun requireCanEditModel(model: Models) {
        requireMcpModelAllowed(model.id)
        requireAllowed(canEditModel(model))
    }

    fun requireCanViewModel(model: Models) {
        requireMcpModelAllowed(model.id)
        requireAllowed(canViewModel(model))
    }

    fun requireCanEditNotation(notation: Notations) = requireAllowed(canEditNotation(notation))

    fun requireCanViewNotation(notation: Notations) = requireAllowed(canViewNotation(notation))

    /**
     * Allows direct notation access, or access through an editable model that actively uses it.
     */
    fun canUseNotationInModelDiagramEditor(notation: Notations, model: Models): Boolean =
        notationDiagramAccess.canUseNotationInModelDiagramEditor(notation, model)

    fun canReferenceNotationForModelDiagram(notation: Notations, model: Models): Boolean =
        canUseNotationInModelDiagramEditor(notation, model)

    fun requireCanReferenceNotationForModelDiagram(notation: Notations, model: Models) =
        requireAllowed(canReferenceNotationForModelDiagram(notation, model))

    fun requireCanEditNodeType(nodeType: NodeTypes) = requireAllowed(canEditNodeType(nodeType))

    fun requireCanEditLinkType(linkType: LinkTypes) = requireAllowed(canEditLinkType(linkType))

    fun requireCanEditNodeShape(shape: NodeShapes) = requireAllowed(canEditNodeShape(shape))

    fun requireCanViewNodeShape(shape: NodeShapes) = requireAllowed(canViewNodeShape(shape))

    fun requireCanEditValidationScript(script: ValidationScripts) =
        requireAllowed(canEditValidationScript(script))

    fun requireCanViewValidationScript(script: ValidationScripts) =
        requireAllowed(canViewValidationScript(script))

    fun requireCanEditNode(node: Nodes) = requireAllowed(canEditNode(node))

    fun requireCanViewNode(node: Nodes) = requireAllowed(canViewNode(node))

    fun requireCanEditLink(link: Links) = requireAllowed(canEditLink(link))

    fun requireCanViewLink(link: Links) = requireAllowed(canViewLink(link))

    fun requireCanEditComponent(component: Components) = requireAllowed(canEditComponent(component))

    fun requireCanViewComponent(component: Components) = requireAllowed(canViewComponent(component))

    fun requireCanEditRelation(relation: Relations) = requireAllowed(canEditRelation(relation))

    fun requireCanViewRelation(relation: Relations) = requireAllowed(canViewRelation(relation))

    fun requireCanEditRelationRule(relationRule: RelationRules) =
        requireAllowed(canEditRelationRule(relationRule))

    fun requireCanViewRelationRule(relationRule: RelationRules) =
        requireAllowed(canViewRelationRule(relationRule))

    fun requireCanEditDiagram(diagram: Diagrams) = requireAllowed(canEditDiagram(diagram))

    fun requireCanViewDiagram(diagram: Diagrams) = requireAllowed(canViewDiagram(diagram))

    fun canManageShares(ownerId: UUID): Boolean {
        val userId = CurrentUser.getId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.SHARE,
            action = CerbosAction.MANAGE,
            resourceId = ownerId,
            ownerId = ownerId,
            resourceAttributes = mapOf("isOwner" to (userId == ownerId))
        )
    }

    fun canViewAdminPanel(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.ADMIN_PANEL,
            action = CerbosAction.VIEW,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun canManageUsers(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.USER_ADMIN,
            action = CerbosAction.MANAGE,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanManageUsers() = requireAllowed(canManageUsers())

    fun canCreateFeedback(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.FEEDBACK_ITEM,
            action = CerbosAction.CREATE,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanCreateFeedback() = requireAllowed(canCreateFeedback())

    fun canVoteFeedback(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.FEEDBACK_ITEM,
            action = CerbosAction.VOTE,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanVoteFeedback() = requireAllowed(canVoteFeedback())

    fun canCommentFeedback(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.FEEDBACK_ITEM,
            action = CerbosAction.COMMENT,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanCommentFeedback() = requireAllowed(canCommentFeedback())

    fun canEditOwnFeedback(authorId: UUID, status: String): Boolean {
        val userId = CurrentUser.getId() ?: return false
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.FEEDBACK_ITEM,
            action = CerbosAction.EDIT,
            resourceId = authorId,
            ownerId = authorId,
            resourceAttributes = mapOf(
                "isAuthor" to (userId == authorId),
                "status" to status
            )
        )
    }

    fun requireCanEditOwnFeedback(authorId: UUID, status: String) =
        requireAllowed(canEditOwnFeedback(authorId, status))

    fun canDeleteOwnFeedback(authorId: UUID, status: String): Boolean {
        val userId = CurrentUser.getId() ?: return false
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.FEEDBACK_ITEM,
            action = CerbosAction.DELETE,
            resourceId = authorId,
            ownerId = authorId,
            resourceAttributes = mapOf(
                "isAuthor" to (userId == authorId),
                "status" to status
            )
        )
    }

    fun requireCanDeleteOwnFeedback(authorId: UUID, status: String) =
        requireAllowed(canDeleteOwnFeedback(authorId, status))

    fun canManageFeedback(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.FEEDBACK_ITEM,
            action = CerbosAction.MANAGE,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanManageFeedback() = requireAllowed(canManageFeedback())

    fun canManageRoadmap(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.ROADMAP_MILESTONE,
            action = CerbosAction.MANAGE,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanManageRoadmap() = requireAllowed(canManageRoadmap())

    fun canManageTutorials(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.TUTORIAL_VIDEO,
            action = CerbosAction.MANAGE,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanManageTutorials() = requireAllowed(canManageTutorials())

    fun canManageDownloads(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.DOWNLOAD_ASSET,
            action = CerbosAction.MANAGE,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanManageDownloads() = requireAllowed(canManageDownloads())

    fun canDownloadAsset(): Boolean {
        val userId = currentUserId()
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosResourceKind.DOWNLOAD_ASSET,
            action = CerbosAction.DOWNLOAD,
            resourceId = userId,
            ownerId = userId
        )
    }

    fun requireCanDownloadAsset() = requireAllowed(canDownloadAsset())

    fun modelAccessPermission(model: Models): String? =
        topLevelAccess.modelAccessPermission(model, ::canViewAdminPanel)

    fun modelAccessPermissions(models: Collection<Models>): Map<UUID, String?> =
        topLevelAccess.modelAccessPermissions(models, ::canViewAdminPanel)

    fun notationAccessPermission(notation: Notations): String? =
        topLevelAccess.notationAccessPermission(notation, ::canViewAdminPanel)

    fun notationAccessPermissions(notations: Collection<Notations>): Map<UUID, String?> =
        topLevelAccess.notationAccessPermissions(notations, ::canViewAdminPanel)

    fun nodeTypeAccessPermission(nodeType: NodeTypes): String? =
        topLevelAccess.nodeTypeAccessPermission(nodeType, ::canViewAdminPanel)

    fun nodeTypeAccessPermissions(nodeTypes: Collection<NodeTypes>): Map<UUID, String?> =
        topLevelAccess.nodeTypeAccessPermissions(nodeTypes, ::canViewAdminPanel)

    fun linkTypeAccessPermission(linkType: LinkTypes): String? =
        topLevelAccess.linkTypeAccessPermission(linkType, ::canViewAdminPanel)

    fun linkTypeAccessPermissions(linkTypes: Collection<LinkTypes>): Map<UUID, String?> =
        topLevelAccess.linkTypeAccessPermissions(linkTypes, ::canViewAdminPanel)

    fun nodeShapeAccessPermission(shape: NodeShapes): String? =
        topLevelAccess.nodeShapeAccessPermission(shape, ::canViewAdminPanel)

    fun nodeShapeAccessPermissions(shapes: Collection<NodeShapes>): Map<UUID, String?> =
        topLevelAccess.nodeShapeAccessPermissions(shapes, ::canViewAdminPanel)

    fun validationScriptAccessPermission(script: ValidationScripts): String? =
        topLevelAccess.validationScriptAccessPermission(script, ::canViewAdminPanel)

    fun validationScriptAccessPermissions(scripts: Collection<ValidationScripts>): Map<UUID, String?> =
        topLevelAccess.validationScriptAccessPermissions(scripts, ::canViewAdminPanel)

    private fun isCommonType(owner: Users): Boolean =
        owner.email.equals("system@arepos.local", ignoreCase = true) || !owner.isActive

    private fun requireAllowed(allowed: Boolean) {
        if (!allowed) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
        }
    }

    private fun isMcpModelAllowed(modelId: UUID?): Boolean {
        if (modelId == null) return false
        val details = CurrentUser.mcpAccessDetails() ?: return true
        val allowed = details.modelIds ?: return true
        return modelId in allowed
    }

    private fun requireMcpModelAllowed(modelId: UUID?) {
        if (!isMcpModelAllowed(modelId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "model_not_allowed")
        }
    }
}
