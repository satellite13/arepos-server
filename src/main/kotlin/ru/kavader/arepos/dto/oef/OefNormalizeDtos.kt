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
 *
 * Folders may carry [properties] from the exporter extension namespace
 * (`https://warchi.ru/oef/folder-props/ the Archi plugin marks its auto with `autoCreated`,humanId`, `name`, `folder, which
 * the standard exchange format cannot (OrganizationType has no PropertiesGroup).
 */
data class OefOrganizationNodeDto(
    val label: String? = null,
    val children: List<OefOrganizationNodeDto>? = null,
    val refId: String? = null,
    val refKind: String? = null,
    val properties: Map<String, String> = emptyMap(),
)

data class OefModelDto(
    val id: String,
    val name: String,
)

data class OefElementDto(
    val id: String,
    val type: String,
    val name: String,
    val properties: Map<String, String> = emptyMap(),
)

data class OefRelationshipDto(
    val id: String,
    val type: String,
    val sourceElementId: String,
    val targetElementId: String,
    val name: String = "",
    val properties: Map<String, String> = emptyMap(),
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
