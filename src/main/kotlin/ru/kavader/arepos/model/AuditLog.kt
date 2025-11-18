package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import java.time.Instant
import java.util.*
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "audit_log", schema = "public", indexes = [
    Index(name = "audit_log_table_name_idx", columnList = "table_name"),
    Index(name = "audit_log_operation_idx", columnList = "operation"),
    Index(name = "audit_log_row_id_idx", columnList = "row_id"),
    Index(name = "audit_log_changed_by_idx", columnList = "changed_by"),
    Index(name = "audit_log_changed_at_idx", columnList = "changed_at"),
    Index(name = "audit_log_entity_history_idx", columnList = "table_name, row_id, changed_at DESC")
])
@JsonIgnoreProperties(ignoreUnknown = true)
data class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "table_name", nullable = false)
    val tableName: String,

    @Column(name = "operation", nullable = false)
    val operation: String,

    @Column(name = "row_id", nullable = false)
    val rowId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    val oldValues: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    val newValues: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    val changedBy: Users? = null,

    @Column(name = "changed_at", nullable = false)
    val changedAt: Instant? = null
)
