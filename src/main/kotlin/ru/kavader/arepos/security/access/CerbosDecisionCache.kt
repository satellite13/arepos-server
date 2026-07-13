package ru.kavader.arepos.security.access

import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import ru.kavader.arepos.security.CerbosAction
import ru.kavader.arepos.security.CerbosResourceKind
import java.util.UUID

@Component
class CerbosDecisionCache {
    private data class DecisionCacheKey(
        val resourceKind: CerbosResourceKind,
        val action: CerbosAction,
        val resourceId: UUID,
        val ownerId: UUID?,
        val attrsHash: Int
    )

    fun get(
        resourceKind: CerbosResourceKind,
        action: CerbosAction,
        resourceId: UUID,
        ownerId: UUID?,
        resourceAttributes: Map<String, Any?>
    ): Boolean? = requestDecisionCache()[key(resourceKind, action, resourceId, ownerId, resourceAttributes)]

    fun put(
        resourceKind: CerbosResourceKind,
        action: CerbosAction,
        resourceId: UUID,
        ownerId: UUID?,
        resourceAttributes: Map<String, Any?>,
        value: Boolean
    ) {
        requestDecisionCache()[key(resourceKind, action, resourceId, ownerId, resourceAttributes)] = value
    }

    private fun key(
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
            .joinToString("|") { (attribute, value) -> "$attribute=$value" }
            .hashCode()
    )

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
        // Outside request scope, avoid state that can leak between thread-pool invocations.
        return mutableMapOf()
    }

    private companion object {
        const val REQUEST_CACHE_ATTR = "arepos.authz.request.decision.cache"
    }
}
