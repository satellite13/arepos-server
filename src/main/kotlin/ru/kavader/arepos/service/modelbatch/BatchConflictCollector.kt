package ru.kavader.arepos.service.modelbatch

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.model.BatchConflictItem
import ru.kavader.arepos.dto.model.BatchDeleteEntry
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodesRepository
import java.time.Instant
import java.util.*

@Component
class BatchConflictCollector(
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository
) {
    fun collect(request: BatchSaveRequest, model: Models): List<BatchConflictItem> {
        if (request.force) return emptyList()
        val modelId = requireNotNull(model.id) { "Model id required" }
        val conflicts = mutableListOf<BatchConflictItem>()

        val requestedNodeStableIds = request.nodes.create
            .mapNotNull { create -> create.tempId.toUUIDOrNull() }
            .toSet()
        if (requestedNodeStableIds.isNotEmpty()) {
            val existingNodes = nodesRepository.findByModelIdAndStableIdIn(modelId, requestedNodeStableIds)
            existingNodes.forEach { node ->
                conflicts.add(
                    BatchConflictItem(
                        kind = "node",
                        id = requireNotNull(node.id),
                        serverUpdatedAt = node.updatedAt,
                        clientBaseUpdatedAt = null
                    )
                )
            }
        }

        conflicts.addVersionConflicts(
            kind = "node",
            modelId = modelId,
            changes = versionedChanges(request.nodes.update, request.nodes.delete, { it.id }, { it.baseUpdatedAt }),
            load = { nodesRepository.findById(it).orElse(null) },
            updatedAt = { it.updatedAt },
            entityModelId = { it.model.id }
        )
        conflicts.addVersionConflicts(
            kind = "link",
            modelId = modelId,
            changes = versionedChanges(request.links.update, request.links.delete, { it.id }, { it.baseUpdatedAt }),
            load = { linksRepository.findById(it).orElse(null) },
            updatedAt = { it.updatedAt },
            entityModelId = { it.model.id }
        )
        conflicts.addVersionConflicts(
            kind = "diagram",
            modelId = modelId,
            changes = versionedChanges(
                request.diagrams.update,
                request.diagrams.delete,
                { it.id },
                { it.baseUpdatedAt }),
            load = { diagramsRepository.findById(it).orElse(null) },
            updatedAt = { it.updatedAt },
            entityModelId = { it.model.id }
        )

        return conflicts
    }

    private data class VersionedChange(val id: UUID, val baseUpdatedAt: Instant?)

    private fun <T> versionedChanges(
        updates: List<T>,
        deletes: List<BatchDeleteEntry>,
        idOf: (T) -> UUID,
        baseOf: (T) -> Instant?
    ): List<VersionedChange> =
        updates.map { VersionedChange(idOf(it), baseOf(it)) } +
                deletes.map { VersionedChange(it.id, it.baseUpdatedAt) }

    private inline fun <T> MutableList<BatchConflictItem>.addVersionConflicts(
        kind: String,
        modelId: UUID,
        changes: Iterable<VersionedChange>,
        load: (UUID) -> T?,
        updatedAt: (T) -> Instant?,
        entityModelId: (T) -> UUID?
    ) {
        for (change in changes) {
            val base = change.baseUpdatedAt ?: continue
            val entity = load(change.id) ?: continue
            if (entityModelId(entity) != modelId) continue
            if (isVersionConflict(updatedAt(entity), base)) {
                add(BatchConflictItem(kind, change.id, updatedAt(entity), base))
            }
        }
    }

    private fun isVersionConflict(server: Instant?, clientBase: Instant): Boolean {
        if (server == null) return true
        return server.toEpochMilli() != clientBase.toEpochMilli()
    }

    private fun String.toUUIDOrNull(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
}
