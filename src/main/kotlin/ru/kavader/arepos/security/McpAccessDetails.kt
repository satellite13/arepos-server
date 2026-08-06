package ru.kavader.arepos.security

import ru.kavader.arepos.dto.apikey.ApiKeyModes
import ru.kavader.arepos.dto.apikey.ApiKeyScopes
import java.util.*

data class McpAccessDetails(
    val mode: String,
    val scopes: Set<String>?,
    /** modelId -> scopes for mode=grants */
    val grants: Map<UUID, Set<String>>?
) {
    fun hasScopeSomewhere(scope: String): Boolean =
        when (mode) {
            ApiKeyModes.ALL -> scopes?.contains(scope) == true
            ApiKeyModes.GRANTS -> grants?.values?.any { scope in it } == true
            else -> false
        }

    fun scopesForModel(modelId: UUID): Set<String>? =
        when (mode) {
            ApiKeyModes.ALL -> scopes
            ApiKeyModes.GRANTS -> grants?.get(modelId)
            else -> null
        }
}
