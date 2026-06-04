package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.dashboard.DashboardRecentResponse
import ru.kavader.arepos.dto.dashboard.DashboardStatsResponse
import ru.kavader.arepos.dto.system.AuditMapper
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Dashboard statistics and activity endpoints")
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
    @Operation(summary = "Get dashboard statistics")
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
    @Operation(summary = "Get recent dashboard activity")
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
            activity = activity.map { auditMapper.toActivityItem(it) }
        )
    }


}
