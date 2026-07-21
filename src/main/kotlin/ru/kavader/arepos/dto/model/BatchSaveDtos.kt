package ru.kavader.arepos.dto.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import ru.kavader.arepos.util.BatchDeleteListDeserializer
import java.time.Instant
import java.util.*

data class BatchDeleteEntry(
    val id: UUID,
    val baseUpdatedAt: Instant? = null
)

data class BatchSaveRequest(
    val force: Boolean = false,
    @field:Valid
    val nodes: BatchNodeOps = BatchNodeOps(),
    @field:Valid
    val links: BatchLinkOps = BatchLinkOps(),
    @field:Valid
    val diagrams: BatchDiagramOps = BatchDiagramOps()
)

data class BatchNodeOps(
    @field:Size(max = 1000)
    @field:Valid
    val create: List<BatchNodeCreate> = emptyList(),
    @field:Size(max = 1000)
    @field:Valid
    val update: List<BatchNodeUpdate> = emptyList(),
    @param:JsonDeserialize(using = BatchDeleteListDeserializer::class)
    @field:Size(max = 1000)
    @field:Valid
    val delete: List<BatchDeleteEntry> = emptyList()
)

data class BatchNodeCreate(
    @field:NotBlank
    @field:Size(max = 255)
    val tempId: String,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    val nodeTypeId: UUID,
    @field:Size(max = 255)
    val parentNodeId: String? = null,
    @field:Size(max = 100000)
    val attrs: String? = null
)

data class BatchNodeUpdate(
    val id: UUID,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    val nodeTypeId: UUID,
    @field:Size(max = 255)
    val parentNodeId: String? = null,
    @field:Size(max = 100000)
    val attrs: String? = null,
    val baseUpdatedAt: Instant? = null
)

data class BatchLinkOps(
    @field:Size(max = 1000)
    @field:Valid
    val create: List<BatchLinkCreate> = emptyList(),
    @field:Size(max = 1000)
    @field:Valid
    val update: List<BatchLinkUpdate> = emptyList(),
    @param:JsonDeserialize(using = BatchDeleteListDeserializer::class)
    @field:Size(max = 1000)
    @field:Valid
    val delete: List<BatchDeleteEntry> = emptyList()
)

data class BatchLinkCreate(
    @field:NotBlank
    @field:Size(max = 255)
    val tempId: String,
    @field:NotBlank
    @field:Size(max = 255)
    val sourceId: String,
    @field:NotBlank
    @field:Size(max = 255)
    val targetId: String,
    val linkTypeId: UUID,
    @field:Size(max = 100000)
    val attrs: String? = null
)

data class BatchLinkUpdate(
    val id: UUID,
    @field:NotBlank
    @field:Size(max = 255)
    val sourceId: String,
    @field:NotBlank
    @field:Size(max = 255)
    val targetId: String,
    val linkTypeId: UUID,
    @field:Size(max = 100000)
    val attrs: String? = null,
    val baseUpdatedAt: Instant? = null
)

data class BatchDiagramOps(
    @field:Size(max = 1000)
    @field:Valid
    val create: List<BatchDiagramCreate> = emptyList(),
    @field:Size(max = 1000)
    @field:Valid
    val update: List<BatchDiagramUpdate> = emptyList(),
    @param:JsonDeserialize(using = BatchDeleteListDeserializer::class)
    @field:Size(max = 1000)
    @field:Valid
    val delete: List<BatchDeleteEntry> = emptyList()
)

data class BatchDiagramCreate(
    @field:NotBlank
    @field:Size(max = 255)
    val tempId: String,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 64)
    val version: String,
    val notationId: UUID,
    @field:Size(max = 255)
    val nodeId: String? = null,
    // Large OEF views pack many node/edge instances into one JSON attrs blob.
    @field:Size(max = 5_000_000)
    val attrs: String? = null
)

data class BatchDiagramUpdate(
    val id: UUID,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 64)
    val version: String,
    val notationId: UUID,
    @field:Size(max = 255)
    val nodeId: String? = null,
    @field:Size(max = 5_000_000)
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

class BatchSaveConflictException(
    val conflicts: List<BatchConflictItem>
) : RuntimeException("Batch save conflict: ${conflicts.size} entity(ies)") {
    init {
        require(conflicts.isNotEmpty()) { "BatchConflictItem list must not be empty" }
    }
}
