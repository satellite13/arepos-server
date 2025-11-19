package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.AuditLog
import ru.kavader.arepos.model.Users
import java.util.UUID

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    fun findByTableName(tableName: String, pageable: Pageable): Page<AuditLog>
    fun findByOperation(operation: String, pageable: Pageable): Page<AuditLog>
    fun findByChangedBy(changedBy: Users, pageable: Pageable): Page<AuditLog>
    fun findByRowId(rowId: UUID, pageable: Pageable): Page<AuditLog>
    fun findByTableNameAndRowId(tableName: String, rowId: UUID, pageable: Pageable): Page<AuditLog>
}


