package ru.kavader.arepos.dto.dashboard

import java.time.Instant
import java.util.UUID

data class DashboardStatsResponse(
    val models: Int,
    val notations: Int,
    val nodeTypes: Int,
    val linkTypes: Int
)

data class DashboardRecentResponse(
    val models: List<DashboardRecentItem>,
    val notations: List<DashboardRecentItem>,
    val activity: List<DashboardActivityItem>
)

data class DashboardRecentItem(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val updatedAt: Instant?
)

data class DashboardActivityItem(
    val id: UUID,
    val tableName: String,
    val operation: String,
    val rowId: UUID,
    val changedById: UUID?,
    val changedAt: Instant?
)
