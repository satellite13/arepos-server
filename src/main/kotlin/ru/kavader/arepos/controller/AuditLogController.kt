package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.AuditLog
import ru.kavader.arepos.repository.AuditLogRepository
import ru.kavader.arepos.repository.UsersRepository
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit-log")
class AuditLogController(
    private val auditLogRepository: AuditLogRepository,
    private val usersRepository: UsersRepository
) {

    @GetMapping
    fun listAuditLogs(
        pageable: Pageable,
        @RequestParam(required = false) tableName: String?,
        @RequestParam(required = false) operation: String?,
        @RequestParam(required = false) changedById: UUID?,
        @RequestParam(required = false) rowId: UUID?
    ): Page<AuditLogResponse> {
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
        return auditLogs.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getAuditLog(@PathVariable id: UUID): AuditLogResponse =
        auditLogRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "AuditLog $id not found")
            }

    private fun AuditLog.toResponse() = AuditLogResponse(
        id = requireNotNull(id),
        tableName = tableName,
        operation = operation,
        rowId = rowId,
        oldValues = oldValues,
        newValues = newValues,
        changedById = changedBy?.id,
        changedAt = changedAt
    )
}

data class AuditLogResponse(
    val id: UUID,
    val tableName: String,
    val operation: String,
    val rowId: UUID,
    val oldValues: String?,
    val newValues: String?,
    val changedById: UUID?,
    val changedAt: java.time.Instant?
)

