package ru.kavader.arepos.controller

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.dashboard.*
import ru.kavader.arepos.dto.system.*
import ru.kavader.arepos.repository.AuditLogRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.model.SharePermission
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
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val auditMapper: AuditMapper
) {
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

    @GetMapping("/stats")
    fun getStats(): DashboardStatsResponse {
        if (accessService.canViewAdminPanel()) {
            return DashboardStatsResponse(
                models = modelsRepository.countDistinctNamesUndeleted().toInt(),
                notations = notationsRepository.countDistinctNamesUndeleted().toInt(),
                nodeTypes = nodeTypesRepository.count().toInt(),
                linkTypes = linkTypesRepository.count().toInt()
            )
        }

        val currentUserId = accessService.currentUserId()

        return DashboardStatsResponse(
            models = modelsRepository.countDistinctAccessibleNamesForUser(currentUserId, viewPermissions).toInt(),
            notations = notationsRepository.countDistinctAccessibleNamesForUser(currentUserId, viewPermissions).toInt(),
            nodeTypes = nodeTypesRepository.count().toInt(),
            linkTypes = linkTypesRepository.count().toInt()
        )
    }

    @GetMapping("/recent")
    fun getRecent(@RequestParam(defaultValue = "5") limit: Int): DashboardRecentResponse {
        val recentSort = Sort.by(Sort.Direction.DESC, "updatedAt")
        val pageable = PageRequest.of(0, limit, recentSort)

        val models = if (accessService.canViewAdminPanel()) {
            modelsRepository.findAll(pageable).content
        } else {
            modelsRepository.findAccessibleForUser(
                userId = accessService.currentUserId(),
                ownerId = null,
                name = "",
                viewPermissions = viewPermissions,
                pageable = pageable
            ).content
        }

        val notations = if (accessService.canViewAdminPanel()) {
            notationsRepository.findAll(pageable).content
        } else {
            notationsRepository.findAccessibleForUser(
                userId = accessService.currentUserId(),
                ownerId = null,
                name = "",
                viewPermissions = viewPermissions,
                pageable = pageable
            ).content
        }

        val activityPageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "changedAt"))
        val activity = if (accessService.canViewAdminPanel()) {
            auditLogRepository.findAll(activityPageable).content
        } else {
            val currentUser = usersRepository.findById(accessService.currentUserId()).orElseThrow()
            auditLogRepository.findByChangedBy(currentUser, activityPageable).content
        }

        return DashboardRecentResponse(
            models = models.map { auditMapper.toRecentItem(it) },
            notations = notations.map { auditMapper.toRecentItem(it) },
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


}
