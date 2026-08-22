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
