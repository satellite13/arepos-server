package ru.kavader.arepos.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Bulk-inserts relation_rules via JDBC.
 *
 * Used by notation/package import to avoid per-row JPA find/exists/save for large
 * Archimate-style rule sets. Relies on the unique constraint
 * (relation, from_component, to_component) with ON CONFLICT DO NOTHING.
 */
@Component
class RelationRulesBulkInserter(
    private val jdbcTemplate: JdbcTemplate
) {
    data class Row(
        val relationId: UUID,
        val fromComponentId: UUID,
        val toComponentId: UUID,
        val ownerId: UUID,
        val createdAt: Instant,
        val updatedAt: Instant = createdAt,
        val id: UUID = UUID.randomUUID()
    )

    fun insertIgnoreConflicts(rows: Collection<Row>, batchSize: Int = DEFAULT_BATCH_SIZE): Int {
        if (rows.isEmpty()) return 0
        val sql = """
            INSERT INTO relation_rules (
                id, created_at, updated_at, owner, attrs, relation, from_component, to_component
            ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT relation_rules_relation_from_to_key DO NOTHING
            """.trimIndent()

        var inserted = 0
        val size = batchSize.coerceAtLeast(1)
        val batchCounts = jdbcTemplate.batchUpdate(sql, rows.toList(), size) { ps, row ->
            ps.setObject(1, row.id)
            ps.setTimestamp(2, Timestamp.from(row.createdAt))
            ps.setTimestamp(3, Timestamp.from(row.updatedAt))
            ps.setObject(4, row.ownerId)
            ps.setObject(5, row.relationId)
            ps.setObject(6, row.fromComponentId)
            ps.setObject(7, row.toComponentId)
        }
        for (counts in batchCounts) {
            for (count in counts) {
                inserted += when {
                    count > 0 -> count
                    count == Statement.SUCCESS_NO_INFO -> 1
                    else -> 0
                }
            }
        }
        return inserted
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 500
    }
}
