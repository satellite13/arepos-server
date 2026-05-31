package ru.kavader.arepos.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class CustomMetricsService(meterRegistry: MeterRegistry) {
    // Auth
    val authLoginSuccess: Counter = Counter.builder("arepos_auth_login_success_total")
        .description("Successful login attempts")
        .register(meterRegistry)

    val authLoginFailure: Counter = Counter.builder("arepos_auth_login_failure_total")
        .description("Failed login attempts")
        .register(meterRegistry)

    // Batch save operations (per entity type and operation)
    private val batchSaveNodeCreate = batchSaveCounter("node", "create", meterRegistry)
    private val batchSaveNodeUpdate = batchSaveCounter("node", "update", meterRegistry)
    private val batchSaveNodeDelete = batchSaveCounter("node", "delete", meterRegistry)
    private val batchSaveLinkCreate = batchSaveCounter("link", "create", meterRegistry)
    private val batchSaveLinkUpdate = batchSaveCounter("link", "update", meterRegistry)
    private val batchSaveLinkDelete = batchSaveCounter("link", "delete", meterRegistry)
    private val batchSaveDiagramCreate = batchSaveCounter("diagram", "create", meterRegistry)
    private val batchSaveDiagramUpdate = batchSaveCounter("diagram", "update", meterRegistry)
    private val batchSaveDiagramDelete = batchSaveCounter("diagram", "delete", meterRegistry)

    // Diagram edit locks
    val editLockAcquire: Counter = Counter.builder("arepos_diagram_edit_lock_acquire_total")
        .description("Diagram edit lock acquire attempts")
        .register(meterRegistry)

    val editLockRelease: Counter = Counter.builder("arepos_diagram_edit_lock_release_total")
        .description("Diagram edit lock releases")
        .register(meterRegistry)

    // Batch save conflicts
    val batchSaveConflicts: Counter = Counter.builder("arepos_batch_save_conflicts_total")
        .description("Batch save conflict occurrences")
        .register(meterRegistry)

    fun incrementBatchNodeCreate(count: Double = 1.0) { batchSaveNodeCreate.increment(count) }
    fun incrementBatchNodeUpdate(count: Double = 1.0) { batchSaveNodeUpdate.increment(count) }
    fun incrementBatchNodeDelete(count: Double = 1.0) { batchSaveNodeDelete.increment(count) }
    fun incrementBatchLinkCreate(count: Double = 1.0) { batchSaveLinkCreate.increment(count) }
    fun incrementBatchLinkUpdate(count: Double = 1.0) { batchSaveLinkUpdate.increment(count) }
    fun incrementBatchLinkDelete(count: Double = 1.0) { batchSaveLinkDelete.increment(count) }
    fun incrementBatchDiagramCreate(count: Double = 1.0) { batchSaveDiagramCreate.increment(count) }
    fun incrementBatchDiagramUpdate(count: Double = 1.0) { batchSaveDiagramUpdate.increment(count) }
    fun incrementBatchDiagramDelete(count: Double = 1.0) { batchSaveDiagramDelete.increment(count) }

    private fun batchSaveCounter(entityType: String, operation: String, registry: MeterRegistry): Counter =
        Counter.builder("arepos_batch_save_operations_total")
            .tag("entity_type", entityType)
            .tag("operation", operation)
            .description("Batch save operations")
            .register(registry)
}
