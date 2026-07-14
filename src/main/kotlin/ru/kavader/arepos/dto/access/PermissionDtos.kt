package ru.kavader.arepos.dto.access

import jakarta.validation.constraints.NotNull
import java.util.*

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
    @field:NotNull val resourceType: PermissionResourceType? = null,
    @field:NotNull val resourceId: UUID? = null,
    val actions: List<PermissionAction> = listOf(PermissionAction.VIEW)
)

data class PermissionCheckResponse(
    val resourceType: PermissionResourceType,
    val resourceId: UUID,
    val decisions: Map<String, Boolean>
)
