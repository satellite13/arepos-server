package ru.kavader.arepos.dto.model

import java.time.Instant
import java.util.UUID

data class ValidationReportResponse(
    val modelId: UUID,
    val generatedAt: Instant,
    val duplicateNodes: List<DuplicateNodeGroup>,
    val duplicateLinks: List<DuplicateLinkGroup>,
    val duplicateNodesTotal: Int,
    val duplicateLinksTotal: Int,
    val diagramIssues: List<DiagramIssueGroup> = emptyList(),
    val diagramIssuesTotal: Int = 0,
    val unusedNodes: List<UnusedNode> = emptyList(),
    val unusedLinks: List<UnusedLink> = emptyList(),
    val unusedNodesTotal: Int = 0,
    val unusedLinksTotal: Int = 0
)

data class UnusedNode(
    val id: UUID,
    val name: String,
    val parentId: UUID? = null,
    val parentName: String? = null
)

data class UnusedLink(
    val id: UUID,
    val sourceName: String,
    val targetName: String,
    val linkTypeName: String
)

data class DeleteUnusedRequest(
    val nodeIds: List<UUID> = emptyList(),
    val linkIds: List<UUID> = emptyList()
)

data class DeleteUnusedResponse(
    val deletedNodeIds: List<UUID> = emptyList(),
    val deletedLinkIds: List<UUID> = emptyList(),
    val skippedNodeIds: List<UUID> = emptyList(),
    val skippedLinkIds: List<UUID> = emptyList()
)

data class DiagramIssue(
    val code: String,
    val level: String,
    val instanceId: String? = null,
    val message: String
)

data class DiagramIssueGroup(
    val diagramId: UUID,
    val diagramName: String,
    val issues: List<DiagramIssue>
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
    val parentName: String?,
    val diagramCount: Int = 0
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
    val id: UUID,
    val diagramCount: Int = 0
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
    val reparentChildren: Boolean = false,
    val keepUpdatedAt: Instant,
    val dropUpdatedAt: Instant
)

data class MergeNodesResponse(
    val keepId: UUID,
    val dropId: UUID
)

data class MergeLinksRequest(
    val keepId: UUID,
    val dropId: UUID,
    val typeProperties: Map<String, Any?> = emptyMap(),
    val keepUpdatedAt: Instant,
    val dropUpdatedAt: Instant
)

data class MergeLinksResponse(
    val keepId: UUID,
    val dropId: UUID
)

data class AutoMergeLockStatus(
    val locked: Boolean,
    val lockedBy: UUID? = null,
    val lockedByName: String? = null,
    val lockedAt: Instant? = null
)
