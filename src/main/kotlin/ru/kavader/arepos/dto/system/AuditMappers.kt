package ru.kavader.arepos.dto.system

import ru.kavader.arepos.model.AuditLog
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.dto.dashboard.DashboardActivityItem
import ru.kavader.arepos.dto.dashboard.DashboardRecentItem

fun AuditLog.toResponse(): AuditLogResponse = AuditLogResponse(
    id = requireNotNull(id),
    tableName = tableName,
    operation = operation,
    rowId = rowId,
    oldValues = oldValues,
    newValues = newValues,
    changedById = changedBy?.id,
    changedAt = changedAt
)

fun Models.toRecentItem(): DashboardRecentItem = DashboardRecentItem(
    id = id!!,
    name = name,
    version = version,
    ownerId = owner.id!!,
    updatedAt = updatedAt
)

fun Notations.toRecentItem(): DashboardRecentItem = DashboardRecentItem(
    id = id!!,
    name = name,
    version = version,
    ownerId = owner.id!!,
    updatedAt = updatedAt
)
