package ru.kavader.arepos.dto.access

import jakarta.validation.constraints.NotNull
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import java.time.Instant
import java.util.*

data class AccessShareRequest(
    @field:NotNull val resourceType: ShareResourceType? = null,
    @field:NotNull val resourceId: UUID? = null,
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
