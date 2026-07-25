package ru.kavader.arepos.dto.oef

data class OefNormalizeResponse(
    val model: OefModelDto,
    val elements: List<OefElementDto>,
    val relationships: List<OefRelationshipDto>,
    val views: List<OefViewDto>,
    val organizations: List<OefOrganizationNodeDto> = emptyList(),
    val issues: List<OefImportIssueDto>,
)

/**
 * Organization tree node. Folder: [label] + [children]. Leaf: [refId] + [refKind].
 * [refKind] is one of: element, relationship, view.
 */
data class OefOrganizationNodeDto(
    val label: String? = null,
    val children: List<OefOrganizationNodeDto>? = null,
    val refId: String? = null,
    val refKind: String? = null,
)

data class OefModelDto(
    val id: String,
    val name: String,
)

data class OefElementDto(
    val id: String,
    val type: String,
    val name: String,
)

data class OefRelationshipDto(
    val id: String,
    val type: String,
    val sourceElementId: String,
    val targetElementId: String,
    val name: String = "",
)

data class OefViewNodeDto(
    val id: String,
    val elementId: String,
    val type: String,
    val x: Double,
    val y: Double,
    val width: Double? = null,
    val height: Double? = null,
    val labelText: String? = null,
)

data class OefViewConnectionDto(
    val id: String,
    val relationshipId: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val type: String,
)

data class OefViewDto(
    val id: String,
    val type: String,
    val name: String,
    val nodes: List<OefViewNodeDto>,
    val connections: List<OefViewConnectionDto>,
)

data class OefImportIssueDto(
    val code: String,
    val level: String,
    val message: String,
    val entityId: String? = null,
    val viewId: String? = null,
)
