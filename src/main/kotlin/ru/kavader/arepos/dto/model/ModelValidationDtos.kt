package ru.kavader.arepos.dto.model

import java.time.Instant
import java.util.UUID

data class ValidationReportResponse(
    val modelId: UUID,
    val generatedAt: Instant,
    val duplicateNodes: List<DuplicateNodeGroup>,
    val duplicateLinks: List<DuplicateLinkGroup>
)

data class DuplicateNodeGroup(
    val nodeTypeId: UUID,
    val nodeTypeName: String,
    val name: String,
    val count: Int,
    val nodes: List<DuplicateNodeMember>
)

data class DuplicateNodeMember(
    val id: UUID,
    val name: String,
    val parentId: UUID?,
    val parentName: String?
)

data class DuplicateLinkGroup(
    val sourceId: UUID,
    val sourceName: String,
    val targetId: UUID,
    val targetName: String,
    val linkTypeId: UUID,
    val linkTypeName: String,
    val count: Int,
    val links: List<DuplicateLinkMember>
)

data class DuplicateLinkMember(
    val id: UUID
)

data class DiagramRef(
    val diagramId: UUID,
    val diagramName: String
)

data class PreviewIncidentLink(
    val id: UUID,
    val linkTypeId: UUID,
    val linkTypeName: String,
    val direction: String,
    val otherNodeId: UUID,
    val otherNodeName: String
)

data class MergeNodesPreviewResponse(
    val keepId: UUID,
    val dropId: UUID,
    val keepTypeProperties: Map<String, Any?>,
    val dropTypeProperties: Map<String, Any?>,
    val uniqueLinks: List<PreviewIncidentLink>,
    val linksToDelete: List<PreviewIncidentLink>,
    val keepDiagrams: List<DiagramRef>,
    val dropDiagrams: List<DiagramRef>,
    val hasChildren: Boolean,
    val hasDocuments: Boolean,
    val diagramsToReparentCount: Long,
    val keepUpdatedAt: Instant,
    val dropUpdatedAt: Instant
)

data class MergeLinksPreviewResponse(
    val keepId: UUID,
    val dropId: UUID,
    val keepTypeProperties: Map<String, Any?>,
    val dropTypeProperties: Map<String, Any?>,
    val keepDiagrams: List<DiagramRef>,
    val dropDiagrams: List<DiagramRef>,
    val keepUpdatedAt: Instant,
    val dropUpdatedAt: Instant
)

data class MergeNodesRequest(
    val keepId: UUID,
    val dropId: UUID,
    val typeProperties: Map<String, Any?> = emptyMap(),
    val transferLinkIds: List<UUID> = emptyList(),
    val keepUpdatedAt: Instant,
    val dropUpdatedAt: Instant
)

data class MergeNodesResponse(
    val keepId: UUID,
    val dropId: UUID
)
