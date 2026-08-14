package ru.kavader.arepos.dto.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

enum class DiagramCopyMatchReason { STABLE_ID, NAME_AND_TYPE, ENDPOINTS_AND_TYPE }

enum class DiagramCopyResolutionAction { MATCH, CREATE, SKIP }

data class DiagramCopyResolution(
    @field:NotNull val sourceId: UUID,
    @field:NotNull val action: DiagramCopyResolutionAction,
    val targetId: UUID? = null, // required when action == MATCH
    val kind: DiagramCopyEntityKind
)

enum class DiagramCopyEntityKind { NODE, LINK }

data class DiagramCopyPreviewRequest(
    @field:NotNull val sourceDiagramId: UUID,
    @field:NotNull val targetNotationId: UUID,
    val resolutions: List<DiagramCopyResolution> = emptyList()
)

data class DiagramCopyCommitRequest(
    @field:NotNull val sourceDiagramId: UUID,
    @field:NotNull val targetNotationId: UUID,
    @field:NotBlank val name: String,
    @field:NotBlank val version: String,
    val nodeId: UUID? = null, // diagram folder in target tree
    val createParentNodeId: UUID? = null, // parent for created nodes (v1 folder/root)
    @field:NotNull val resolutions: List<DiagramCopyResolution>
)

data class DiagramCopyCandidate(
    val id: UUID,
    val label: String,
    val stableId: UUID?,
    val typeId: UUID?
)

data class DiagramCopyEntityPreview(
    val sourceId: UUID,
    val kind: DiagramCopyEntityKind,
    val label: String,
    val stableId: UUID?,
    val typeId: UUID?,
    val autoMatchTargetId: UUID? = null,
    val autoMatchReason: DiagramCopyMatchReason? = null,
    val candidates: List<DiagramCopyCandidate> = emptyList(),
    val effectiveAction: DiagramCopyResolutionAction? = null,
    val effectiveTargetId: UUID? = null,
    val isEndpointOfEdge: Boolean = false
)

data class DiagramCopyEdgeBlocker(
    val edgeInstanceId: String,
    val modelLinkId: UUID?,
    val sourceModelNodeId: UUID?,
    val targetModelNodeId: UUID?,
    val code: String,
    val reason: String
)

data class DiagramCopyNotationRemapReport(
    val mappedComponents: Int,
    val unmappedComponents: List<String>,
    val mappedRelations: Int,
    val unmappedRelations: List<String>
)

data class DiagramCopyWarning(
    val code: String,
    val message: String
)

data class DiagramCopyPreviewResponse(
    val sourceDiagramId: UUID,
    val sourceDiagramName: String,
    val sourceDiagramVersion: String,
    val suggestedName: String,
    val suggestedVersion: String,
    val nodes: List<DiagramCopyEntityPreview>,
    val links: List<DiagramCopyEntityPreview>,
    val blockers: List<DiagramCopyEdgeBlocker>,
    val notationRemap: DiagramCopyNotationRemapReport,
    val warnings: List<DiagramCopyWarning>,
    val canCommit: Boolean
)

data class DiagramCopyCommitResponse(
    val diagram: DiagramResponse,
    val createdNodeIds: List<UUID>,
    val createdLinkIds: List<UUID>
)
