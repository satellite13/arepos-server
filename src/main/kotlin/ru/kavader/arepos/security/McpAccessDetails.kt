package ru.kavader.arepos.security

import java.util.*

data class McpAccessDetails(
    val scopes: Set<String>,
    /** null = all models the user can access; non-null = allowlist */
    val modelIds: Set<UUID>?
)
