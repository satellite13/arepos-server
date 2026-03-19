package ru.kavader.arepos.controller

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.repository.AuditLogRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController(
    private val modelsRepository: ModelsRepository,
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val auditLogRepository: AuditLogRepository,
    private val accessService: ResourceAccessService
) {

    @GetMapping("/stats")
    fun getStats(): DashboardStatsResponse {
        if (CurrentUser.isAdmin()) {
            val uniqueModelNames = modelsRepository.findAll(Pageable.unpaged()).content
                .map { it.name }
                .distinct()
                .size
            val uniqueNotationNames = notationsRepository.findAll(Pageable.unpaged()).content
                .map { it.name }
                .distinct()
                .size
            return DashboardStatsResponse(
                models = uniqueModelNames,
                notations = uniqueNotationNames,
                nodeTypes = nodeTypesRepository.count().toInt(),
                linkTypes = linkTypesRepository.count().toInt()
            )
        }

        val accessibleModels = modelsRepository.findAll(Pageable.unpaged()).content
            .filter { accessService.canViewModel(it) }
        val accessibleNotations = notationsRepository.findAll(Pageable.unpaged()).content
            .filter { accessService.canViewNotation(it) }

        return DashboardStatsResponse(
            models = accessibleModels.map { it.name }.distinct().size,
            notations = accessibleNotations.map { it.name }.distinct().size,
            nodeTypes = nodeTypesRepository.count().toInt(),
            linkTypes = linkTypesRepository.count().toInt()
        )
    }

    @GetMapping("/recent")
    fun getRecent(@RequestParam(defaultValue = "5") limit: Int): DashboardRecentResponse {
        val recentSort = Sort.by(Sort.Direction.DESC, "updatedAt")
        val pageable = PageRequest.of(0, limit, recentSort)

        val models = if (CurrentUser.isAdmin()) {
            modelsRepository.findAll(pageable).content
        } else {
            modelsRepository.findAll(PageRequest.of(0, limit * 3, recentSort)).content
                .filter { accessService.canViewModel(it) }
                .take(limit)
        }

        val notations = if (CurrentUser.isAdmin()) {
            notationsRepository.findAll(pageable).content
        } else {
            notationsRepository.findAll(PageRequest.of(0, limit * 3, recentSort)).content
                .filter { accessService.canViewNotation(it) }
                .take(limit)
        }

        val activityPageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "changedAt"))
        val activity = auditLogRepository.findAll(activityPageable).content

        return DashboardRecentResponse(
            models = models.map { it.toRecentItem() },
            notations = notations.map { it.toRecentItem() },
            activity = activity.map {
                DashboardActivityItem(
                    id = it.id!!,
                    tableName = it.tableName,
                    operation = it.operation,
                    rowId = it.rowId,
                    changedById = it.changedBy?.id,
                    changedAt = it.changedAt
                )
            }
        )
    }

    private fun ru.kavader.arepos.model.Models.toRecentItem() = DashboardRecentItem(
        id = id!!,
        name = name,
        version = version,
        ownerId = owner.id!!,
        updatedAt = updatedAt
    )

    private fun ru.kavader.arepos.model.Notations.toRecentItem() = DashboardRecentItem(
        id = id!!,
        name = name,
        version = version,
        ownerId = owner.id!!,
        updatedAt = updatedAt
    )
}

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
