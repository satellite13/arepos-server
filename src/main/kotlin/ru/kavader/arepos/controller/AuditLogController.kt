package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.dto.system.AuditLogResponse
import ru.kavader.arepos.mapper.AuditMapper
import ru.kavader.arepos.model.AuditLog
import ru.kavader.arepos.repository.AuditLogRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ACCESS_DENIED
import ru.kavader.arepos.security.ResourceAccessService
import java.util.*

@RestController
@RequestMapping("/api/v1/audit-log")
@Tag(name = "Audit Log", description = "Audit log browsing endpoints")
class AuditLogController(
    private val auditLogRepository: AuditLogRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val auditMapper: AuditMapper
) {

    @GetMapping
    @Operation(summary = "List audit log entries")
    fun listAuditLogs(
        pageable: Pageable,
        @RequestParam(required = false) tableName: String?,
        @RequestParam(required = false) operation: String?,
        @RequestParam(required = false) changedById: UUID?,
        @RequestParam(required = false) rowId: UUID?
    ): ListResponse<AuditLogResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            if (changedById != null && changedById != currentUserId) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
            }
            val currentUser = usersRepository.findById(currentUserId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $currentUserId not found")
            }
            val auditLogs = when {
                tableName != null && rowId != null -> {
                    auditLogRepository.findByTableNameAndRowId(tableName, rowId, pageable)
                }

                tableName != null -> {
                    auditLogRepository.findByTableName(tableName, pageable)
                }

                operation != null -> {
                    auditLogRepository.findByOperation(operation, pageable)
                }

                rowId != null -> {
                    auditLogRepository.findByRowId(rowId, pageable)
                }

                else -> {
                    auditLogRepository.findByChangedBy(currentUser, pageable)
                }
            }
            val filtered = auditLogs.content.filter { it.changedBy?.id == currentUserId }
            return PageImpl(filtered, pageable, filtered.size.toLong())
                .map { auditMapper.toResponse(it) }
                .toListResponse()
        }

        val auditLogs = when {
            tableName != null && rowId != null -> {
                auditLogRepository.findByTableNameAndRowId(tableName, rowId, pageable)
            }

            tableName != null -> {
                auditLogRepository.findByTableName(tableName, pageable)
            }

            operation != null -> {
                auditLogRepository.findByOperation(operation, pageable)
            }

            changedById != null -> {
                val changedBy = usersRepository.findById(changedById).orElse(null)
                if (changedBy != null) {
                    auditLogRepository.findByChangedBy(changedBy, pageable)
                } else {
                    auditLogRepository.findAll(pageable)
                }
            }

            rowId != null -> {
                auditLogRepository.findByRowId(rowId, pageable)
            }

            else -> {
                auditLogRepository.findAll(pageable)
            }
        }
        return auditLogs.map { auditMapper.toResponse(it) }.toListResponse()
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get audit log entry by id")
    fun getAuditLog(@PathVariable id: UUID): AuditLogResponse =
        auditLogRepository.findById(id)
            .map {
                checkAuditLogReadable(it)
                auditMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "AuditLog $id not found")
            }

    private fun checkAuditLogReadable(auditLog: AuditLog) {
        if (accessService.canViewAdminPanel()) return
        val currentUserId = accessService.currentUserId()
        if (auditLog.changedBy?.id != currentUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
        }
    }


}
