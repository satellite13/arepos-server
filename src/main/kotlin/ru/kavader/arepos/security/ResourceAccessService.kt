package ru.kavader.arepos.security

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
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
        private const val REQUEST_CACHE_ATTR = "arepos.authz.request.decision.cache"
    }
    private val localDecisionCache = ThreadLocal.withInitial { mutableMapOf<DecisionCacheKey, Boolean>() }

    private val viewPermissions = setOf(SharePermission.VIEW, SharePermission.EDIT)
    private data class ShareFlags(val hasView: Boolean, val hasEdit: Boolean)
    private data class DecisionCacheKey(
        val resourceKind: CerbosResourceKind,
        val action: CerbosAction,
        val resourceId: UUID,
        val ownerId: UUID?,
        val attrsHash: Int
    )
    private data class TopLevelDecisionInput(
        val resourceKind: CerbosResourceKind,
        val action: CerbosAction,
        val resourceId: UUID,
        val ownerId: UUID,
        val isOwner: Boolean,
        val shareFlags: ShareFlags
    )

    fun currentUserId(): UUID = CurrentUser.getId()
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

    fun canEditModel(model: Models): Boolean = canEditTopLevel(model.owner.id!!, ShareResourceType.MODEL, model.id!!)

    fun canViewModel(model: Models): Boolean = canViewTopLevel(model.owner.id!!, ShareResourceType.MODEL, model.id!!)

    fun canViewModels(models: Collection<Models>): Map<UUID, Boolean> =
        evaluateTopLevelBatch(
            entries = models.mapNotNull { model ->
                model.id?.let { id ->
                    Triple(model.owner.id!!, ShareResourceType.MODEL, id)
                }
            },
            action = CerbosAction.VIEW
        )

    fun canViewNotations(notations: Collection<Notations>): Map<UUID, Boolean> =
        evaluateTopLevelBatch(
            entries = notations.mapNotNull { notation ->
                notation.id?.let { id ->
                    Triple(notation.owner.id!!, ShareResourceType.NOTATION, id)
                }
            },
            action = CerbosAction.VIEW
        )

    fun filterViewableModels(models: Collection<Models>): List<Models> {
        if (CurrentUser.isAdmin()) {
            return models.toList()
        }
        val decisions = canViewModels(models)
        return models.filter { model -> model.id?.let { decisions[it] } == true }
    }

    fun filterViewableNotations(notations: Collection<Notations>): List<Notations> {
        if (CurrentUser.isAdmin()) {
            return notations.toList()
        }
        val decisions = canViewNotations(notations)
        return notations.filter { notation -> notation.id?.let { decisions[it] } == true }
    }

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
            resourceKind = CerbosResourceKind.FILE,
            action = CerbosAction.VIEW,
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
        val isOwner = userId == ownerId
        val legacyDecision = CurrentUser.isAdmin() || userId == ownerId
        // AccessShares is owner/admin controlled; use owner UUID as resource id.
        return applyCerbosDecision(
            resourceKind = CerbosResourceKind.SHARE,
            action = CerbosAction.MANAGE,
            resourceId = ownerId,
            ownerId = ownerId,
            legacyDecision = legacyDecision,
            resourceAttributes = mapOf("isOwner" to isOwner)
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
        val isAdmin = CurrentUser.isAdmin()
        val userId = CurrentUser.getId()
        val isOwner = userId != null && ownerId == userId
        val shareFlags = resolveShareFlags(resourceType, resourceId, userId)
        val legacyDecision = isAdmin || isOwner || shareFlags.hasEdit

        return applyCerbosDecision(
            resourceKind = CerbosMappers.fromShareResourceType(resourceType),
            action = CerbosAction.EDIT,
            resourceId = resourceId,
            ownerId = ownerId,
            legacyDecision = legacyDecision,
            resourceAttributes = mapOf(
                "isOwner" to isOwner,
                "hasShareView" to shareFlags.hasView,
                "hasShareEdit" to shareFlags.hasEdit
            )
        )
    }

    private fun canViewTopLevel(ownerId: UUID, resourceType: ShareResourceType, resourceId: UUID): Boolean {
        val isAdmin = CurrentUser.isAdmin()
        val userId = CurrentUser.getId()
        val isOwner = userId != null && ownerId == userId
        val shareFlags = resolveShareFlags(resourceType, resourceId, userId)
        val legacyDecision = isAdmin || isOwner || shareFlags.hasView

        return applyCerbosDecision(
            resourceKind = CerbosMappers.fromShareResourceType(resourceType),
            action = CerbosAction.VIEW,
            resourceId = resourceId,
            ownerId = ownerId,
            legacyDecision = legacyDecision,
            resourceAttributes = mapOf(
                "isOwner" to isOwner,
                "hasShareView" to shareFlags.hasView,
                "hasShareEdit" to shareFlags.hasEdit
            )
        )
    }

    private fun applyCerbosDecision(
        resourceKind: CerbosResourceKind,
        action: CerbosAction,
        resourceId: UUID,
        ownerId: UUID?,
        legacyDecision: Boolean,
        resourceAttributes: Map<String, Any?> = emptyMap()
    ): Boolean {
        val cacheKey = decisionCacheKey(resourceKind, action, resourceId, ownerId, resourceAttributes)
        readDecisionFromRequestCache(cacheKey)?.let { cached ->
            authzObservabilityService.recordFinalDecision(
                resourceKind.policyValue,
                action.policyValue,
                "request_cache",
                cached
            )
            return cached
        }

        fun cacheAndReturn(value: Boolean): Boolean {
            storeDecisionInRequestCache(cacheKey, value)
            return value
        }

        authzObservabilityService.recordLegacyDecision(resourceKind.policyValue, action.policyValue, legacyDecision)

        if (!cerbosProperties.enabled || cerbosProperties.mode == CerbosMode.DISABLED) {
            authzObservabilityService.recordFinalDecision(
                resourceKind.policyValue,
                action.policyValue,
                "legacy_disabled",
                legacyDecision
            )
            return cacheAndReturn(legacyDecision)
        }

        val startNanos = System.nanoTime()
        val cerbosDecision = try {
            cerbosDecisionService.check(
                CerbosAccessRequest(
                    resourceKind = resourceKind,
                    action = action,
                    resourceId = resourceId,
                    ownerId = ownerId,
                    resourceAttributes = resourceAttributes
                )
            )
        } catch (ex: Exception) {
            authzObservabilityService.recordCerbosRequest(
                resourceKind.policyValue,
                action.policyValue,
                "error",
                System.nanoTime() - startNanos
            )
            if (cerbosProperties.failOpen) {
                log.warn("Cerbos check failed, fail-open active. resourceKind={}, action={}, resourceId={}", resourceKind, action, resourceId, ex)
                authzObservabilityService.recordFinalDecision(
                    resourceKind.policyValue,
                    action.policyValue,
                    "legacy_fail_open",
                    legacyDecision
                )
                return cacheAndReturn(legacyDecision)
            }
            log.warn("Cerbos check failed, fail-open disabled. Falling back to legacy decision to preserve current behavior. resourceKind={}, action={}, resourceId={}", resourceKind, action, resourceId, ex)
            authzObservabilityService.recordFinalDecision(
                resourceKind.policyValue,
                action.policyValue,
                "legacy_fail_closed_fallback",
                legacyDecision
            )
            return cacheAndReturn(legacyDecision)
        }

        authzObservabilityService.recordCerbosRequest(
            resourceKind = resourceKind.policyValue,
            action = action.policyValue,
            outcome = if (cerbosDecision == null) "not_implemented" else "ok",
            durationNanos = System.nanoTime() - startNanos
        )

        if (cerbosDecision == null) {
            authzObservabilityService.recordFinalDecision(
                resourceKind.policyValue,
                action.policyValue,
                "legacy_no_cerbos_decision",
                legacyDecision
            )
            return cacheAndReturn(legacyDecision)
        }

        if (cerbosProperties.shadowEnabled || cerbosProperties.mode == CerbosMode.SHADOW) {
            authzObservabilityService.recordShadowComparison(
                resourceKind.policyValue,
                action.policyValue,
                legacyDecision,
                cerbosDecision
            )
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
            authzObservabilityService.recordFinalDecision(
                resourceKind.policyValue,
                action.policyValue,
                "legacy_shadow",
                legacyDecision
            )
            return cacheAndReturn(legacyDecision)
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
            authzObservabilityService.recordFinalDecision(
                resourceKind.policyValue,
                action.policyValue,
                "cerbos_enforce",
                cerbosDecision
            )
            return cacheAndReturn(cerbosDecision)
        }

        authzObservabilityService.recordFinalDecision(
            resourceKind.policyValue,
            action.policyValue,
            "legacy_mode_fallback",
            legacyDecision
        )
        return cacheAndReturn(legacyDecision)
    }

    private fun evaluateTopLevelBatch(
        entries: Collection<Triple<UUID, ShareResourceType, UUID>>,
        action: CerbosAction
    ): Map<UUID, Boolean> {
        if (entries.isEmpty()) {
            return emptyMap()
        }
        val isAdmin = CurrentUser.isAdmin()
        val userId = CurrentUser.getId()
        if (isAdmin) {
            return entries.associate { (_, _, resourceId) -> resourceId to true }
        }
        if (userId == null) {
            return entries.associate { (_, _, resourceId) -> resourceId to false }
        }

        val byType = entries.groupBy { it.second }
        val shareFlagsByType = byType.mapValues { (resourceType, groupedEntries) ->
            resolveShareFlagsBatch(
                resourceType = resourceType,
                resourceIds = groupedEntries.map { it.third }.toSet(),
                userId = userId
            )
        }

        val inputs = entries.map { (ownerId, resourceType, resourceId) ->
            val shareFlags = shareFlagsByType[resourceType]?.get(resourceId) ?: ShareFlags(hasView = false, hasEdit = false)
            val isOwner = ownerId == userId
            TopLevelDecisionInput(
                resourceKind = CerbosMappers.fromShareResourceType(resourceType),
                action = action,
                resourceId = resourceId,
                ownerId = ownerId,
                isOwner = isOwner,
                shareFlags = shareFlags
            )
        }

        return applyCerbosDecisionBatch(inputs)
    }

    private fun applyCerbosDecisionBatch(inputs: List<TopLevelDecisionInput>): Map<UUID, Boolean> {
        if (inputs.isEmpty()) {
            return emptyMap()
        }

        val legacyByResource = inputs.associate { input ->
            val legacyDecision = when (input.action) {
                CerbosAction.VIEW -> input.isOwner || input.shareFlags.hasView
                CerbosAction.EDIT -> input.isOwner || input.shareFlags.hasEdit
                CerbosAction.MANAGE -> input.isOwner
            }
            input.resourceId to legacyDecision
        }

        val unresolved = mutableListOf<TopLevelDecisionInput>()
        val resolved = mutableMapOf<UUID, Boolean>()
        inputs.forEach { input ->
            val attrs = mapOf(
                "isOwner" to input.isOwner,
                "hasShareView" to input.shareFlags.hasView,
                "hasShareEdit" to input.shareFlags.hasEdit
            )
            val cacheKey = decisionCacheKey(
                resourceKind = input.resourceKind,
                action = input.action,
                resourceId = input.resourceId,
                ownerId = input.ownerId,
                resourceAttributes = attrs
            )
            val cached = readDecisionFromRequestCache(cacheKey)
            if (cached != null) {
                authzObservabilityService.recordFinalDecision(
                    input.resourceKind.policyValue,
                    input.action.policyValue,
                    "request_cache",
                    cached
                )
                resolved[input.resourceId] = cached
            } else {
                unresolved += input
            }
        }

        if (unresolved.isEmpty()) {
            return resolved
        }

        unresolved.forEach { input ->
            authzObservabilityService.recordLegacyDecision(
                input.resourceKind.policyValue,
                input.action.policyValue,
                legacyByResource[input.resourceId] == true
            )
        }

        if (!cerbosProperties.enabled || cerbosProperties.mode == CerbosMode.DISABLED) {
            unresolved.forEach { input ->
                val legacyDecision = legacyByResource[input.resourceId] == true
                authzObservabilityService.recordFinalDecision(
                    input.resourceKind.policyValue,
                    input.action.policyValue,
                    "legacy_disabled",
                    legacyDecision
                )
                resolved[input.resourceId] = legacyDecision
                storeDecisionInRequestCache(
                    decisionCacheKey(
                        input.resourceKind,
                        input.action,
                        input.resourceId,
                        input.ownerId,
                        mapOf(
                            "isOwner" to input.isOwner,
                            "hasShareView" to input.shareFlags.hasView,
                            "hasShareEdit" to input.shareFlags.hasEdit
                        )
                    ),
                    legacyDecision
                )
            }
            return resolved
        }

        val groupedByKindAndAction = unresolved.groupBy { it.resourceKind to it.action }
        groupedByKindAndAction.forEach { (kindAction, groupedInputs) ->
            val (resourceKind, action) = kindAction
            val requestStartNanos = System.nanoTime()
            val decisionsById = try {
                cerbosDecisionService.checkBatch(
                    groupedInputs.map { input ->
                        CerbosBatchAccessRequest(
                            resourceKind = input.resourceKind,
                            action = input.action,
                            resourceId = input.resourceId,
                            ownerId = input.ownerId,
                            resourceAttributes = mapOf(
                                "isOwner" to input.isOwner,
                                "hasShareView" to input.shareFlags.hasView,
                                "hasShareEdit" to input.shareFlags.hasEdit
                            )
                        )
                    }
                )
            } catch (ex: Exception) {
                groupedInputs.forEach { input ->
                    authzObservabilityService.recordCerbosRequest(
                        resourceKind = input.resourceKind.policyValue,
                        action = input.action.policyValue,
                        outcome = "error",
                        durationNanos = System.nanoTime() - requestStartNanos
                    )
                    val legacyDecision = legacyByResource[input.resourceId] == true
                    if (cerbosProperties.failOpen) {
                        log.warn(
                            "Cerbos batch check failed, fail-open active. resourceKind={}, action={}, resourceId={}",
                            input.resourceKind,
                            input.action,
                            input.resourceId,
                            ex
                        )
                        authzObservabilityService.recordFinalDecision(
                            input.resourceKind.policyValue,
                            input.action.policyValue,
                            "legacy_fail_open",
                            legacyDecision
                        )
                    } else {
                        log.warn(
                            "Cerbos batch check failed, fail-open disabled. Falling back to legacy decision. resourceKind={}, action={}, resourceId={}",
                            input.resourceKind,
                            input.action,
                            input.resourceId,
                            ex
                        )
                        authzObservabilityService.recordFinalDecision(
                            input.resourceKind.policyValue,
                            input.action.policyValue,
                            "legacy_fail_closed_fallback",
                            legacyDecision
                        )
                    }
                    resolved[input.resourceId] = legacyDecision
                    storeDecisionInRequestCache(
                        decisionCacheKey(
                            input.resourceKind,
                            input.action,
                            input.resourceId,
                            input.ownerId,
                            mapOf(
                                "isOwner" to input.isOwner,
                                "hasShareView" to input.shareFlags.hasView,
                                "hasShareEdit" to input.shareFlags.hasEdit
                            )
                        ),
                        legacyDecision
                    )
                }
                return@forEach
            }

            groupedInputs.forEach { input ->
                val decisionDuration = System.nanoTime() - requestStartNanos
                val cerbosDecision = decisionsById[input.resourceId]
                authzObservabilityService.recordCerbosRequest(
                    resourceKind = resourceKind.policyValue,
                    action = action.policyValue,
                    outcome = if (cerbosDecision == null) "not_implemented" else "ok",
                    durationNanos = decisionDuration
                )
                val legacyDecision = legacyByResource[input.resourceId] == true

                val finalDecision = when {
                    cerbosDecision == null -> {
                        authzObservabilityService.recordFinalDecision(
                            resourceKind.policyValue,
                            action.policyValue,
                            "legacy_no_cerbos_decision",
                            legacyDecision
                        )
                        legacyDecision
                    }

                    cerbosProperties.shadowEnabled || cerbosProperties.mode == CerbosMode.SHADOW -> {
                        authzObservabilityService.recordShadowComparison(
                            resourceKind.policyValue,
                            action.policyValue,
                            legacyDecision,
                            cerbosDecision
                        )
                        if (legacyDecision != cerbosDecision) {
                            log.debug(
                                "Cerbos shadow mismatch: resourceKind={}, action={}, resourceId={}, legacy={}, cerbos={}",
                                resourceKind,
                                action,
                                input.resourceId,
                                legacyDecision,
                                cerbosDecision
                            )
                        }
                        authzObservabilityService.recordFinalDecision(
                            resourceKind.policyValue,
                            action.policyValue,
                            "legacy_shadow",
                            legacyDecision
                        )
                        legacyDecision
                    }

                    cerbosProperties.enforceEnabled || cerbosProperties.mode == CerbosMode.ENFORCE -> {
                        if (legacyDecision != cerbosDecision) {
                            log.info(
                                "Cerbos enforce override: resourceKind={}, action={}, resourceId={}, legacy={}, cerbos={}",
                                resourceKind,
                                action,
                                input.resourceId,
                                legacyDecision,
                                cerbosDecision
                            )
                        }
                        authzObservabilityService.recordFinalDecision(
                            resourceKind.policyValue,
                            action.policyValue,
                            "cerbos_enforce",
                            cerbosDecision
                        )
                        cerbosDecision
                    }

                    else -> {
                        authzObservabilityService.recordFinalDecision(
                            resourceKind.policyValue,
                            action.policyValue,
                            "legacy_mode_fallback",
                            legacyDecision
                        )
                        legacyDecision
                    }
                }

                resolved[input.resourceId] = finalDecision
                storeDecisionInRequestCache(
                    decisionCacheKey(
                        input.resourceKind,
                        input.action,
                        input.resourceId,
                        input.ownerId,
                        mapOf(
                            "isOwner" to input.isOwner,
                            "hasShareView" to input.shareFlags.hasView,
                            "hasShareEdit" to input.shareFlags.hasEdit
                        )
                    ),
                    finalDecision
                )
            }
        }

        return resolved
    }

    private fun resolveShareFlags(resourceType: ShareResourceType, resourceId: UUID, userId: UUID?): ShareFlags {
        if (userId == null) {
            return ShareFlags(hasView = false, hasEdit = false)
        }

        val hasEdit = resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            granteeUserId = userId,
            permission = SharePermission.EDIT
        ) || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            permission = SharePermission.EDIT
        )

        val hasView = hasEdit || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermissionIn(
            resourceType = resourceType,
            resourceId = resourceId,
            granteeUserId = userId,
            permissions = viewPermissions
        ) || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermissionIn(
            resourceType = resourceType,
            resourceId = resourceId,
            permissions = viewPermissions
        )

        return ShareFlags(hasView = hasView, hasEdit = hasEdit)
    }

    private fun resolveShareFlagsBatch(
        resourceType: ShareResourceType,
        resourceIds: Set<UUID>,
        userId: UUID?
    ): Map<UUID, ShareFlags> {
        if (resourceIds.isEmpty() || userId == null) {
            return resourceIds.associateWith { ShareFlags(hasView = false, hasEdit = false) }
        }

        val userShares = resourceSharesRepository.findByResourceTypeAndResourceIdInAndGranteeUserIdAndPermissionIn(
            resourceType = resourceType,
            resourceIds = resourceIds,
            granteeUserId = userId,
            permissions = viewPermissions
        )
        val publicShares = resourceSharesRepository.findByResourceTypeAndResourceIdInAndGranteeUserIsNullAndPermissionIn(
            resourceType = resourceType,
            resourceIds = resourceIds,
            permissions = viewPermissions
        )

        val resolved = resourceIds.associateWith { ShareFlags(hasView = false, hasEdit = false) }.toMutableMap()
        (userShares + publicShares).forEach { share ->
            val resourceId = share.resourceId
            val previous = resolved[resourceId] ?: ShareFlags(hasView = false, hasEdit = false)
            val hasEdit = previous.hasEdit || share.permission == SharePermission.EDIT
            resolved[resourceId] = ShareFlags(
                hasView = true,
                hasEdit = hasEdit
            )
        }
        return resolved
    }

    private fun decisionCacheKey(
        resourceKind: CerbosResourceKind,
        action: CerbosAction,
        resourceId: UUID,
        ownerId: UUID?,
        resourceAttributes: Map<String, Any?>
    ): DecisionCacheKey = DecisionCacheKey(
        resourceKind = resourceKind,
        action = action,
        resourceId = resourceId,
        ownerId = ownerId,
        attrsHash = resourceAttributes.entries
            .sortedBy { it.key }
            .joinToString("|") { (key, value) -> "$key=$value" }
            .hashCode()
    )

    private fun readDecisionFromRequestCache(key: DecisionCacheKey): Boolean? =
        requestDecisionCache()[key]

    private fun storeDecisionInRequestCache(key: DecisionCacheKey, value: Boolean) {
        requestDecisionCache()[key] = value
    }

    private fun requestDecisionCache(): MutableMap<DecisionCacheKey, Boolean> {
        val attributes = RequestContextHolder.getRequestAttributes()
        if (attributes != null) {
            val existing = attributes.getAttribute(REQUEST_CACHE_ATTR, RequestAttributes.SCOPE_REQUEST)
            if (existing is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                return existing as MutableMap<DecisionCacheKey, Boolean>
            }
            val created = mutableMapOf<DecisionCacheKey, Boolean>()
            attributes.setAttribute(REQUEST_CACHE_ATTR, created, RequestAttributes.SCOPE_REQUEST)
            return created
        }
        return localDecisionCache.get()
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
