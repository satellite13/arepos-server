package ru.kavader.arepos.dto.access

import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import java.time.Instant
import java.util.UUID

data class AccessShareRequest(
    val resourceType: ShareResourceType? = null,
    val resourceId: UUID? = null,
    val granteeUserId: UUID? = null,
    val permission: SharePermission? = null
)

data class AccessShareResponse(
    val id: UUID,
    val resourceType: ShareResourceType,
    val resourceId: UUID,
    val granteeUserId: UUID?,
    val grantedByUserId: UUID,
    val permission: String,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
