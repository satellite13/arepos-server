package ru.kavader.arepos.dto.dashboard

import java.time.Instant
import java.util.*

data class DashboardStatsResponse(
    val models: Int,
    val notations: Int,
    val nodeTypes: Int,
    val linkTypes: Int
)

data class DashboardRecentResponse(
    val models: List<DashboardRecentItem>,
    val notations: List<DashboardRecentItem>,
    val diagrams: List<DashboardRecentDiagramItem>
)

data class DashboardRecentItem(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val updatedAt: Instant?
)

data class DashboardRecentDiagramItem(
    val id: UUID,
    val name: String,
    val version: String,
    val modelId: UUID,
    val modelName: String,
    val updatedAt: Instant?
)
