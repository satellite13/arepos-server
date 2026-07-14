package ru.kavader.arepos.security.access

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.security.AuthzObservabilityService
import ru.kavader.arepos.security.CerbosAccessRequest
import ru.kavader.arepos.security.CerbosAction
import ru.kavader.arepos.security.CerbosBatchAccessRequest
import ru.kavader.arepos.security.CerbosDecisionService
import ru.kavader.arepos.security.CerbosMappers
import ru.kavader.arepos.security.CerbosResourceKind
import ru.kavader.arepos.security.CurrentUser
import java.util.UUID

@Component
class BatchEvaluator(
    private val shareResolver: ShareResolver,
    private val cerbosDecisionService: CerbosDecisionService,
    private val authzObservabilityService: AuthzObservabilityService,
    private val decisionCache: CerbosDecisionCache
) {
    private data class TopLevelDecisionInput(
        val resourceKind: CerbosResourceKind,
        val action: CerbosAction,
        val resourceId: UUID,
        val ownerId: UUID,
        val isOwner: Boolean,
        val shareFlags: ShareFlags
    ) {
        val attributes: Map<String, Any?>
            get() = mapOf(
                "isOwner" to isOwner,
                "hasShareView" to shareFlags.hasView,
                "hasShareEdit" to shareFlags.hasEdit
            )
    }

    fun applyCerbosDecision(
        resourceKind: CerbosResourceKind,
        action: CerbosAction,
        resourceId: UUID,
        ownerId: UUID?,
        resourceAttributes: Map<String, Any?> = emptyMap()
    ): Boolean {
        decisionCache.get(resourceKind, action, resourceId, ownerId, resourceAttributes)?.let { cached ->
            recordFinalDecision(resourceKind, action, "request_cache", cached)
            return cached
        }

        val startNanos = System.nanoTime()
        val decision = try {
            cerbosDecisionService.check(
                CerbosAccessRequest(
                    resourceKind = resourceKind,
                    action = action,
                    resourceId = resourceId,
                    ownerId = ownerId,
                    resourceAttributes = resourceAttributes
                )
            ).also {
                authzObservabilityService.recordCerbosRequest(
                    resourceKind.policyValue,
                    action.policyValue,
                    "ok",
                    System.nanoTime() - startNanos
                )
            }
        } catch (ex: Exception) {
            authzObservabilityService.recordCerbosRequest(
                resourceKind.policyValue,
                action.policyValue,
                "error",
                System.nanoTime() - startNanos
            )
            throw cerbosUnavailable(resourceKind, action, resourceId, ex)
        }

        recordFinalDecision(resourceKind, action, "cerbos_enforce", decision)
        decisionCache.put(resourceKind, action, resourceId, ownerId, resourceAttributes, decision)
        return decision
    }

    fun evaluateTopLevelBatch(
        entries: Collection<Triple<UUID, ShareResourceType, UUID>>,
        action: CerbosAction
    ): Map<UUID, Boolean> {
        if (entries.isEmpty()) {
            return emptyMap()
        }
        val userId = CurrentUser.getId()
            ?: return entries.associate { (_, _, resourceId) -> resourceId to false }

        val shareFlagsByType = entries.groupBy { it.second }.mapValues { (resourceType, groupedEntries) ->
            shareResolver.resolveShareFlagsBatch(
                resourceType = resourceType,
                resourceIds = groupedEntries.map { it.third }.toSet(),
                userId = userId
            )
        }
        val inputs = entries.map { (ownerId, resourceType, resourceId) ->
            TopLevelDecisionInput(
                resourceKind = CerbosMappers.fromShareResourceType(resourceType),
                action = action,
                resourceId = resourceId,
                ownerId = ownerId,
                isOwner = ownerId == userId,
                shareFlags = shareFlagsByType[resourceType]?.get(resourceId)
                    ?: ShareFlags(hasView = false, hasEdit = false)
            )
        }
        return applyCerbosDecisionBatch(inputs)
    }

    private fun applyCerbosDecisionBatch(inputs: List<TopLevelDecisionInput>): Map<UUID, Boolean> {
        if (inputs.isEmpty()) {
            return emptyMap()
        }

        val unresolved = mutableListOf<TopLevelDecisionInput>()
        val resolved = mutableMapOf<UUID, Boolean>()
        inputs.forEach { input ->
            val cached = decisionCache.get(
                input.resourceKind,
                input.action,
                input.resourceId,
                input.ownerId,
                input.attributes
            )
            if (cached != null) {
                recordFinalDecision(input.resourceKind, input.action, "request_cache", cached)
                resolved[input.resourceId] = cached
            } else {
                unresolved += input
            }
        }

        unresolved.groupBy { it.resourceKind to it.action }.forEach { (kindAction, groupedInputs) ->
            val (resourceKind, action) = kindAction
            val startNanos = System.nanoTime()
            val decisionsById = try {
                cerbosDecisionService.checkBatch(
                    groupedInputs.map { input ->
                        CerbosBatchAccessRequest(
                            resourceKind = input.resourceKind,
                            action = input.action,
                            resourceId = input.resourceId,
                            ownerId = input.ownerId,
                            resourceAttributes = input.attributes
                        )
                    }
                )
            } catch (ex: Exception) {
                groupedInputs.forEach { input ->
                    authzObservabilityService.recordCerbosRequest(
                        input.resourceKind.policyValue,
                        input.action.policyValue,
                        "error",
                        System.nanoTime() - startNanos
                    )
                }
                throw cerbosUnavailable(resourceKind, action, groupedInputs.first().resourceId, ex)
            }

            groupedInputs.forEach { input ->
                val decision = decisionsById[input.resourceId]
                    ?: throw cerbosUnavailable(
                        input.resourceKind,
                        input.action,
                        input.resourceId,
                        IllegalStateException("Cerbos batch response missing decision")
                    )
                authzObservabilityService.recordCerbosRequest(
                    resourceKind.policyValue,
                    action.policyValue,
                    "ok",
                    System.nanoTime() - startNanos
                )
                recordFinalDecision(resourceKind, action, "cerbos_enforce", decision)
                decisionCache.put(
                    input.resourceKind,
                    input.action,
                    input.resourceId,
                    input.ownerId,
                    input.attributes,
                    decision
                )
                resolved[input.resourceId] = decision
            }
        }
        return resolved
    }

    private fun recordFinalDecision(
        resourceKind: CerbosResourceKind,
        action: CerbosAction,
        source: String,
        allowed: Boolean
    ) {
        authzObservabilityService.recordFinalDecision(
            resourceKind.policyValue,
            action.policyValue,
            source,
            allowed
        )
    }

    private fun cerbosUnavailable(
        resourceKind: CerbosResourceKind,
        action: CerbosAction,
        resourceId: UUID,
        cause: Exception
    ): ResponseStatusException {
        log.error(
            "Cerbos unavailable in enforce-only mode. resourceKind={}, action={}, resourceId={}",
            resourceKind,
            action,
            resourceId,
            cause
        )
        return ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Authorization service is unavailable"
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(BatchEvaluator::class.java)
    }
}
