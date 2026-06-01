package ru.kavader.arepos.dto.access

import ru.kavader.arepos.model.ResourceShares

fun ResourceShares.toResponse(): AccessShareResponse = AccessShareResponse(
    id = id!!,
    resourceType = resourceType,
    resourceId = resourceId,
    granteeUserId = granteeUser?.id,
    grantedByUserId = grantedByUser.id!!,
    permission = permission.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)
