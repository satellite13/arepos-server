package ru.kavader.arepos.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.time.Instant
import java.util.UUID

data class BatchDeleteEntry(
    val id: UUID,
    val baseUpdatedAt: Instant? = null
)

data class BatchSaveRequest(
    val force: Boolean = false,
    val nodes: BatchNodeOps = BatchNodeOps(),
    val links: BatchLinkOps = BatchLinkOps(),
    val diagrams: BatchDiagramOps = BatchDiagramOps()
)

data class BatchNodeOps(
    val create: List<BatchNodeCreate> = emptyList(),
    val update: List<BatchNodeUpdate> = emptyList(),
    @param:JsonDeserialize(using = BatchDeleteListDeserializer::class)
    val delete: List<BatchDeleteEntry> = emptyList()
)

data class BatchNodeCreate(
    val tempId: String,
    val name: String,
    val nodeTypeId: UUID,
    val parentNodeId: String? = null,
    val attrs: String? = null
)

data class BatchNodeUpdate(
    val id: UUID,
    val name: String,
    val nodeTypeId: UUID,
    val parentNodeId: String? = null,
    val attrs: String? = null,
    val baseUpdatedAt: Instant? = null
)

data class BatchLinkOps(
    val create: List<BatchLinkCreate> = emptyList(),
    val update: List<BatchLinkUpdate> = emptyList(),
    @param:JsonDeserialize(using = BatchDeleteListDeserializer::class)
    val delete: List<BatchDeleteEntry> = emptyList()
)

data class BatchLinkCreate(
    val tempId: String,
    val sourceId: String,
    val targetId: String,
    val linkTypeId: UUID,
    val attrs: String? = null
)

data class BatchLinkUpdate(
    val id: UUID,
    val sourceId: String,
    val targetId: String,
    val linkTypeId: UUID,
    val attrs: String? = null,
    val baseUpdatedAt: Instant? = null
)

data class BatchDiagramOps(
    val create: List<BatchDiagramCreate> = emptyList(),
    val update: List<BatchDiagramUpdate> = emptyList(),
    @param:JsonDeserialize(using = BatchDeleteListDeserializer::class)
    val delete: List<BatchDeleteEntry> = emptyList()
)

data class BatchDiagramCreate(
    val tempId: String,
    val name: String,
    val version: String,
    val notationId: UUID,
    val nodeId: String? = null,
    val attrs: String? = null
)

data class BatchDiagramUpdate(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val nodeId: String? = null,
    val attrs: String? = null,
    val baseUpdatedAt: Instant? = null
)

data class BatchSaveResponse(
    val nodeIdMap: Map<String, UUID>,
    val linkIdMap: Map<String, UUID>,
    val diagramIdMap: Map<String, UUID>,
    val nodesCreated: Int,
    val nodesUpdated: Int,
    val nodesDeleted: Int,
    val linksCreated: Int,
    val linksUpdated: Int,
    val linksDeleted: Int,
    val diagramsCreated: Int,
    val diagramsUpdated: Int,
    val diagramsDeleted: Int
)

data class BatchConflictItem(
    val kind: String,
    val id: UUID,
    val serverUpdatedAt: Instant?,
    val clientBaseUpdatedAt: Instant?
)

data class BatchSaveConflictBody(
    val message: String,
    val conflicts: List<BatchConflictItem>
)

class BatchSaveConflictException(
    val conflicts: List<BatchConflictItem>
) : RuntimeException("Batch save conflict: ${conflicts.size} entity(ies)") {
    init {
        require(conflicts.isNotEmpty()) { "BatchConflictItem list must not be empty" }
    }
}
