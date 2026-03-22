package ru.kavader.arepos.security

import ru.kavader.arepos.model.ShareResourceType

enum class CerbosResourceKind(val policyValue: String) {
    ADMIN_PANEL("admin_panel"),
    USER_ADMIN("user_admin"),
    MODEL("model"),
    NOTATION("notation"),
    NODE_TYPE("node_type"),
    LINK_TYPE("link_type"),
    NODE_SHAPE("node_shape"),
    FILE("file"),
    SHARE("share")
}

enum class CerbosAction(val policyValue: String) {
    VIEW("view"),
    EDIT("edit"),
    MANAGE("manage")
}

object CerbosMappers {
    fun fromShareResourceType(resourceType: ShareResourceType): CerbosResourceKind =
        when (resourceType) {
            ShareResourceType.MODEL -> CerbosResourceKind.MODEL
            ShareResourceType.NOTATION -> CerbosResourceKind.NOTATION
            ShareResourceType.NODE_TYPE -> CerbosResourceKind.NODE_TYPE
            ShareResourceType.LINK_TYPE -> CerbosResourceKind.LINK_TYPE
            ShareResourceType.NODE_SHAPE -> CerbosResourceKind.NODE_SHAPE
        }
}
