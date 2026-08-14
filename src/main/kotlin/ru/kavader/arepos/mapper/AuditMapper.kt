package ru.kavader.arepos.mapper

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.dashboard.DashboardRecentDiagramItem
import ru.kavader.arepos.dto.dashboard.DashboardRecentItem
import ru.kavader.arepos.dto.system.AuditLogResponse
import ru.kavader.arepos.model.AuditLog
import ru.kavader.arepos.model.Diagrams
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

    fun toRecentDiagramItem(diagram: Diagrams): DashboardRecentDiagramItem = DashboardRecentDiagramItem(
        id = requireNotNull(diagram.id),
        name = diagram.name,
        version = diagram.version,
        modelId = requireNotNull(diagram.model.id),
        modelName = diagram.model.name,
        updatedAt = diagram.updatedAt
    )
}
