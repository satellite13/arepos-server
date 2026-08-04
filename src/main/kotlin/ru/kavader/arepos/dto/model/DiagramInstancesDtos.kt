package ru.kavader.arepos.dto.model

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class DiagramInstanceNodeInput(
    @field:NotNull val modelNodeId: UUID,
    @field:NotNull val x: Double,
    @field:NotNull val y: Double,
    val width: Double? = null,
    val height: Double? = null,
    val id: String? = null
)

data class DiagramInstanceEdgeInput(
    @field:NotNull val modelLinkId: UUID,
    val sourceInstanceId: String? = null,
    val targetInstanceId: String? = null,
    val id: String? = null
)

data class DiagramInstancesMergeRequest(
    val nodes: List<DiagramInstanceNodeInput> = emptyList(),
    val edges: List<DiagramInstanceEdgeInput> = emptyList(),
    val baseUpdatedAt: Instant? = null
)

data class DiagramInstancesMergeCounts(
    val nodesAdded: Int,
    val nodesUpdated: Int,
    val edgesAdded: Int,
    val edgesUpdated: Int
)

data class DiagramInstancesMergeResponse(
    val diagram: DiagramResponse,
    val counts: DiagramInstancesMergeCounts
)

data class EnsureLinkResponse(
    val link: LinkResponse,
    val created: Boolean
)
