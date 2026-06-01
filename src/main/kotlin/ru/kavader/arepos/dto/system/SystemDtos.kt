package ru.kavader.arepos.dto.system

import java.time.Instant
import java.util.UUID

data class VersionResponse(
    val version: String
)

data class AuditLogResponse(
    val id: UUID,
    val tableName: String,
    val operation: String,
    val rowId: UUID,
    val oldValues: String?,
    val newValues: String?,
    val changedById: UUID?,
    val changedAt: Instant?
)
