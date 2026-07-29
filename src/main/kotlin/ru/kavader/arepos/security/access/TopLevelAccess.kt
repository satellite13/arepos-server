package ru.kavader.arepos.security.access

import org.springframework.stereotype.Component
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.ValidationScripts
import ru.kavader.arepos.security.CerbosAction
import ru.kavader.arepos.security.CerbosMappers
import ru.kavader.arepos.security.CurrentUser
import java.util.UUID

@Component
class TopLevelAccess(
    private val shareResolver: ShareResolver,
    private val batchEvaluator: BatchEvaluator
) {
    fun canEditModel(model: Models): Boolean =
        canEdit(model.owner.id!!, ShareResourceType.MODEL, model.id!!)

    fun canViewModel(model: Models): Boolean =
        canView(model.owner.id!!, ShareResourceType.MODEL, model.id!!)

    fun canViewModels(models: Collection<Models>): Map<UUID, Boolean> =
        evaluate(models.mapEntries(ShareResourceType.MODEL) { it.owner.id!! to it.id }, CerbosAction.VIEW)

    fun filterViewableModels(models: Collection<Models>): List<Models> {
        val decisions = canViewModels(models)
        return models.filter { model -> model.id?.let { decisions[it] } == true }
    }

    fun canEditNotation(notation: Notations): Boolean =
        canEdit(notation.owner.id!!, ShareResourceType.NOTATION, notation.id!!)

    fun canViewNotationDirect(notation: Notations): Boolean {
        val id = notation.id ?: return false
        return canView(notation.owner.id!!, ShareResourceType.NOTATION, id)
    }

    fun canViewNotationsDirect(notations: Collection<Notations>): Map<UUID, Boolean> =
        evaluate(notations.mapEntries(ShareResourceType.NOTATION) { it.owner.id!! to it.id }, CerbosAction.VIEW)

    fun canEditNodeType(nodeType: NodeTypes): Boolean =
        canEdit(nodeType.owner.id!!, ShareResourceType.NODE_TYPE, nodeType.id!!)

    fun canViewNodeTypeDirect(nodeType: NodeTypes): Boolean {
        val id = nodeType.id ?: return false
        return canView(nodeType.owner.id!!, ShareResourceType.NODE_TYPE, id)
    }

    fun canEditLinkType(linkType: LinkTypes): Boolean =
        canEdit(linkType.owner.id!!, ShareResourceType.LINK_TYPE, linkType.id!!)

    fun canViewLinkTypeDirect(linkType: LinkTypes): Boolean {
        val id = linkType.id ?: return false
        return canView(linkType.owner.id!!, ShareResourceType.LINK_TYPE, id)
    }

    fun canEditNodeShape(shape: NodeShapes): Boolean =
        canEdit(shape.owner.id!!, ShareResourceType.NODE_SHAPE, shape.id!!)

    fun canViewNodeShape(shape: NodeShapes): Boolean =
        canView(shape.owner.id!!, ShareResourceType.NODE_SHAPE, shape.id!!)

    fun canViewNodeShapes(shapes: Collection<NodeShapes>): Map<UUID, Boolean> =
        evaluate(shapes.mapEntries(ShareResourceType.NODE_SHAPE) { it.owner.id!! to it.id }, CerbosAction.VIEW)

    fun filterViewableNodeShapes(shapes: Collection<NodeShapes>): List<NodeShapes> {
        val decisions = canViewNodeShapes(shapes)
        return shapes.filter { shape -> shape.id?.let { decisions[it] } == true }
    }

    fun canEditValidationScript(script: ValidationScripts): Boolean =
        canEdit(script.owner.id!!, ShareResourceType.VALIDATION_SCRIPT, script.id!!)

    fun canViewValidationScript(script: ValidationScripts): Boolean =
        canView(script.owner.id!!, ShareResourceType.VALIDATION_SCRIPT, script.id!!)

    fun canViewValidationScripts(scripts: Collection<ValidationScripts>): Map<UUID, Boolean> =
        evaluate(
            scripts.mapEntries(ShareResourceType.VALIDATION_SCRIPT) { it.owner.id!! to it.id },
            CerbosAction.VIEW
        )

    fun filterViewableValidationScripts(scripts: Collection<ValidationScripts>): List<ValidationScripts> {
        val decisions = canViewValidationScripts(scripts)
        return scripts.filter { script -> script.id?.let { decisions[it] } == true }
    }

    fun modelAccessPermission(model: Models, isAdmin: () -> Boolean): String? =
        accessPermission(model.owner.id!!, ShareResourceType.MODEL, model.id!!, isAdmin)

    fun modelAccessPermissions(models: Collection<Models>, isAdmin: () -> Boolean): Map<UUID, String?> =
        accessPermissions(models.mapEntries(ShareResourceType.MODEL) { it.owner.id!! to it.id }, isAdmin)

    fun notationAccessPermission(notation: Notations, isAdmin: () -> Boolean): String? =
        accessPermission(notation.owner.id!!, ShareResourceType.NOTATION, notation.id!!, isAdmin)

    fun notationAccessPermissions(notations: Collection<Notations>, isAdmin: () -> Boolean): Map<UUID, String?> =
        accessPermissions(notations.mapEntries(ShareResourceType.NOTATION) { it.owner.id!! to it.id }, isAdmin)

    fun nodeTypeAccessPermission(nodeType: NodeTypes, isAdmin: () -> Boolean): String? =
        accessPermission(nodeType.owner.id!!, ShareResourceType.NODE_TYPE, nodeType.id!!, isAdmin)

    fun nodeTypeAccessPermissions(nodeTypes: Collection<NodeTypes>, isAdmin: () -> Boolean): Map<UUID, String?> =
        accessPermissions(nodeTypes.mapEntries(ShareResourceType.NODE_TYPE) { it.owner.id!! to it.id }, isAdmin)

    fun linkTypeAccessPermission(linkType: LinkTypes, isAdmin: () -> Boolean): String? =
        accessPermission(linkType.owner.id!!, ShareResourceType.LINK_TYPE, linkType.id!!, isAdmin)

    fun linkTypeAccessPermissions(linkTypes: Collection<LinkTypes>, isAdmin: () -> Boolean): Map<UUID, String?> =
        accessPermissions(linkTypes.mapEntries(ShareResourceType.LINK_TYPE) { it.owner.id!! to it.id }, isAdmin)

    fun nodeShapeAccessPermission(shape: NodeShapes, isAdmin: () -> Boolean): String? =
        accessPermission(shape.owner.id!!, ShareResourceType.NODE_SHAPE, shape.id!!, isAdmin)

    fun nodeShapeAccessPermissions(shapes: Collection<NodeShapes>, isAdmin: () -> Boolean): Map<UUID, String?> =
        accessPermissions(shapes.mapEntries(ShareResourceType.NODE_SHAPE) { it.owner.id!! to it.id }, isAdmin)

    fun validationScriptAccessPermission(script: ValidationScripts, isAdmin: () -> Boolean): String? =
        accessPermission(script.owner.id!!, ShareResourceType.VALIDATION_SCRIPT, script.id!!, isAdmin)

    fun validationScriptAccessPermissions(
        scripts: Collection<ValidationScripts>,
        isAdmin: () -> Boolean
    ): Map<UUID, String?> =
        accessPermissions(
            scripts.mapEntries(ShareResourceType.VALIDATION_SCRIPT) { it.owner.id!! to it.id },
            isAdmin
        )

    private fun canEdit(ownerId: UUID, resourceType: ShareResourceType, resourceId: UUID): Boolean =
        evaluate(ownerId, resourceType, resourceId, CerbosAction.EDIT)

    private fun canView(ownerId: UUID, resourceType: ShareResourceType, resourceId: UUID): Boolean =
        evaluate(ownerId, resourceType, resourceId, CerbosAction.VIEW)

    private fun evaluate(
        ownerId: UUID,
        resourceType: ShareResourceType,
        resourceId: UUID,
        action: CerbosAction
    ): Boolean {
        val userId = CurrentUser.getId()
        val shareFlags = shareResolver.resolveShareFlags(resourceType, resourceId, userId)
        return batchEvaluator.applyCerbosDecision(
            resourceKind = CerbosMappers.fromShareResourceType(resourceType),
            action = action,
            resourceId = resourceId,
            ownerId = ownerId,
            resourceAttributes = mapOf(
                "isOwner" to (userId != null && ownerId == userId),
                "hasShareView" to shareFlags.hasView,
                "hasShareEdit" to shareFlags.hasEdit
            )
        )
    }

    private fun evaluate(
        entries: Collection<Triple<UUID, ShareResourceType, UUID>>,
        action: CerbosAction
    ): Map<UUID, Boolean> = batchEvaluator.evaluateTopLevelBatch(entries, action)

    private fun accessPermission(
        ownerId: UUID,
        resourceType: ShareResourceType,
        resourceId: UUID,
        isAdmin: () -> Boolean
    ): String? {
        if (isAdmin()) {
            return "ADMIN"
        }
        val userId = CurrentUser.getId() ?: return null
        if (canEdit(ownerId, resourceType, resourceId)) {
            return if (ownerId == userId) "OWNER" else "EDIT"
        }
        if (canView(ownerId, resourceType, resourceId)) {
            return if (ownerId == userId) "OWNER" else "VIEW"
        }
        return null
    }

    private fun accessPermissions(
        entries: Collection<Triple<UUID, ShareResourceType, UUID>>,
        isAdmin: () -> Boolean
    ): Map<UUID, String?> {
        if (entries.isEmpty()) {
            return emptyMap()
        }
        if (isAdmin()) {
            return entries.associate { (_, _, resourceId) -> resourceId to "ADMIN" }
        }

        val currentUserId = CurrentUser.getId()
        val editDecisions = evaluate(entries, CerbosAction.EDIT)
        val viewDecisions = evaluate(entries, CerbosAction.VIEW)
        return entries.associate { (ownerId, _, resourceId) ->
            resourceId to when {
                editDecisions[resourceId] == true -> if (ownerId == currentUserId) "OWNER" else "EDIT"
                viewDecisions[resourceId] == true -> if (ownerId == currentUserId) "OWNER" else "VIEW"
                else -> null
            }
        }
    }

    private fun <T> Collection<T>.mapEntries(
        resourceType: ShareResourceType,
        identifiers: (T) -> Pair<UUID, UUID?>
    ): List<Triple<UUID, ShareResourceType, UUID>> = mapNotNull { entity ->
        val (ownerId, resourceId) = identifiers(entity)
        resourceId?.let { Triple(ownerId, resourceType, it) }
    }
}
