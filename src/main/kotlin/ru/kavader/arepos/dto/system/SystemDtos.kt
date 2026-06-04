package ru.kavader.arepos.dto.system

import java.time.Instant
import java.util.*

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
