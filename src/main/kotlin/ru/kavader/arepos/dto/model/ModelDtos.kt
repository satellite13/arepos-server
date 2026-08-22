package ru.kavader.arepos.dto.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import ru.kavader.arepos.dto.validation.StorageSemver
import java.time.Instant
import java.util.*

data class ModelRequest(
    @field:NotBlank val name: String,
    @field:NotBlank @field:StorageSemver val version: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class ModelUpdateRequest(
    val name: String? = null,
    @field:StorageSemver val version: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class ModelResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val ownerEmail: String,
    val ownerDisplayName: String,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val sourceId: UUID? = null
)

data class GroupedEntityResponse<T>(val groups: List<EntityGroupResponse<T>>)
data class EntityGroupResponse<T>(val name: String, val versions: List<T>)

data class NodeRequest(
    @field:NotBlank val name: String,
    @field:NotNull var modelId: UUID,
    val ownerId: UUID? = null,
    /** Required unless notation component binding is provided. */
    var nodeTypeId: UUID? = null,
    val parentNodeId: UUID? = null,
    val attrs: String? = null,
    val stableId: UUID? = null,
    val notationId: UUID? = null,
    val componentId: UUID? = null,
    val componentName: String? = null
)

data class NodeUpdateRequest(
    val name: String? = null,
    val modelId: UUID? = null,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val parentNodeId: UUID? = null,
    val attrs: String? = null
)

data class NodeResponse(
    val id: UUID,
    val stableId: UUID,
    val name: String,
    val modelId: UUID,
    val ownerId: UUID,
    val nodeTypeId: UUID,
    val parentNodeId: UUID?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val hasChildren: Boolean? = null
)

data class EnsureNodeResponse(
    val node: NodeResponse,
    val created: Boolean
)

data class AmbiguousNodeCandidate(
    val id: UUID,
    val name: String,
    val parentNodeId: UUID?
)

class AmbiguousNodeException(
    val candidates: List<AmbiguousNodeCandidate>
) : RuntimeException("Ambiguous node (${candidates.size} matches)")

data class LinkRequest(
    @field:NotNull val sourceId: UUID,
    @field:NotNull val targetId: UUID,
    @field:NotNull val modelId: UUID,
    val ownerId: UUID? = null,
    /** Required unless notation relation binding is provided. */
    val linkTypeId: UUID? = null,
    val attrs: String? = null,
    val stableId: UUID? = null,
    val notationId: UUID? = null,
    val relationId: UUID? = null,
    val relationName: String? = null
)

data class LinkUpdateRequest(
    val sourceId: UUID? = null,
    val targetId: UUID? = null,
    val modelId: UUID? = null,
    val ownerId: UUID? = null,
    val linkTypeId: UUID? = null,
    val attrs: String? = null
)

data class LinkResponse(
    val id: UUID,
    val stableId: UUID,
    val sourceId: UUID,
    val targetId: UUID,
    val modelId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class DiagramRequest(
    @field:NotBlank val name: String,
    @field:NotBlank @field:StorageSemver val version: String = "1.0.0",
    val ownerId: UUID? = null,
    @field:NotNull var modelId: UUID,
    val nodeId: UUID? = null,
    @field:NotNull val notationId: UUID,
    val attrs: String? = null
)

data class EnsureDiagramResponse(
    val diagram: DiagramResponse,
    val created: Boolean
)

data class DiagramUpdateRequest(
    val name: String? = null,
    @field:StorageSemver val version: String? = null,
    val ownerId: UUID? = null,
    val modelId: UUID? = null,
    val nodeId: UUID? = null,
    val notationId: UUID? = null,
    val attrs: String? = null
)

data class DiagramResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val modelId: UUID,
    val nodeId: UUID?,
    val notationId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class DiagramShareLinkRequest(
    val diagramId: UUID? = null,
    val modelId: UUID? = null,
    val diagramName: String? = null,
    val latest: Boolean? = null
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "Provide either diagramId or (modelId, diagramName, latest: true)")
    val isTargetSelectionValid: Boolean
        get() {
            val directDiagram = diagramId != null
            val latestNamedDiagram = modelId != null && !diagramName.isNullOrBlank() && latest == true
            return directDiagram.xor(latestNamedDiagram)
        }
}

data class DiagramShareLinkResponse(
    val url: String,
    val token: UUID,
    /** Diagram whose preview SVG the public URL resolves to (pinned id or current latest-by-name). */
    val diagramId: UUID
)

data class DiagramLockStatusResponse(
    val diagramId: UUID,
    val isLocked: Boolean,
    val lockedByUserId: UUID? = null,
    val lockedByDisplay: String? = null,
    val expiresAt: Instant? = null,
    val diagramUpdatedAt: Instant? = null,
    val reason: String? = null
)

data class DiagramPointerRequest(
    val worldX: Double,
    val worldY: Double,
    val visible: Boolean = true
)

data class DiagramSpectatorView(
    val userId: UUID,
    val displayName: String
)
