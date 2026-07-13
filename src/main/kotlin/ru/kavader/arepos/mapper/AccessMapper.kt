package ru.kavader.arepos.mapper

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.access.AccessShareResponse
import ru.kavader.arepos.model.ResourceShares

@Component
class AccessMapper {
    fun toResponse(share: ResourceShares): AccessShareResponse = AccessShareResponse(
        id = requireNotNull(share.id),
        resourceType = share.resourceType,
        resourceId = share.resourceId,
        granteeUserId = share.granteeUser?.id,
        grantedByUserId = share.grantedByUser.id!!,
        permission = share.permission.name,
        createdAt = share.createdAt,
        updatedAt = share.updatedAt
    )
}
