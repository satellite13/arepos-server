package ru.kavader.arepos.dto.system

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.dashboard.DashboardActivityItem
import ru.kavader.arepos.dto.dashboard.DashboardRecentItem
import ru.kavader.arepos.model.AuditLog
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Notations

@Component
class AuditMapper {
    fun toResponse(auditLog: AuditLog): AuditLogResponse = AuditLogResponse(
        id = requireNotNull(auditLog.id),
        tableName = auditLog.tableName,
        operation = auditLog.operation,
        rowId = auditLog.rowId,
        oldValues = auditLog.oldValues,
        newValues = auditLog.newValues,
        changedById = auditLog.changedBy?.id,
        changedAt = auditLog.changedAt
    )

    fun toRecentItem(model: Models): DashboardRecentItem = DashboardRecentItem(
        id = requireNotNull(model.id),
        name = model.name,
        version = model.version,
        ownerId = model.owner.id!!,
        updatedAt = model.updatedAt
    )

    fun toRecentItem(notation: Notations): DashboardRecentItem = DashboardRecentItem(
        id = requireNotNull(notation.id),
        name = notation.name,
        version = notation.version,
        ownerId = notation.owner.id!!,
        updatedAt = notation.updatedAt
    )

    fun toActivityItem(auditLog: AuditLog): DashboardActivityItem = DashboardActivityItem(
        id = requireNotNull(auditLog.id),
        tableName = auditLog.tableName,
        operation = auditLog.operation,
        rowId = auditLog.rowId,
        changedById = auditLog.changedBy?.id,
        changedAt = auditLog.changedAt
    )
}
