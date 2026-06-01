package ru.kavader.arepos.dto.access

import java.util.UUID

enum class PermissionResourceType {
    MODEL,
    NOTATION,
    DIAGRAM,
    NODE_TYPE,
    LINK_TYPE,
    NODE_SHAPE,
    FILE,
    SHARE,
    ADMIN_PANEL
}

enum class PermissionAction {
    VIEW,
    EDIT,
    MANAGE
}

data class PermissionCheckRequest(
    val resourceType: PermissionResourceType? = null,
    val resourceId: UUID? = null,
    val actions: List<PermissionAction> = listOf(PermissionAction.VIEW)
)

data class PermissionCheckResponse(
    val resourceType: PermissionResourceType,
    val resourceId: UUID,
    val decisions: Map<String, Boolean>
)
