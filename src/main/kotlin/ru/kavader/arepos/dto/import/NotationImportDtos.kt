package ru.kavader.arepos.dto.import

import java.util.UUID

data class NotationImportRequest(
    val notation: NotationImportMeta,
    val nodeTypes: List<ImportedNodeType> = emptyList(),
    val linkTypes: List<ImportedLinkType> = emptyList(),
    val components: List<ImportedComponent> = emptyList(),
    val relations: List<ImportedRelation> = emptyList(),
    val relationRules: List<ImportedRelationRule> = emptyList()
)

data class NotationImportMeta(
    val name: String,
    val version: String = "1.0.0",
    val attrs: String? = null
)

data class ImportedNodeType(
    val id: String,
    val name: String,
    val attrs: String? = null
)

data class ImportedLinkType(
    val id: String,
    val name: String,
    val attrs: String? = null
)

data class ImportedComponent(
    val id: String,
    val name: String,
    val nodeTypeId: String,
    val version: String? = null,
    val attrs: String? = null
)

data class ImportedRelation(
    val id: String,
    val name: String,
    val linkTypeId: String,
    val version: String? = null,
    val attrs: String? = null
)

data class ImportedRelationRule(
    val fromComponentId: String,
    val toComponentId: String,
    val allowedRelationIds: List<String> = emptyList()
)

data class NotationImportResponse(
    val notationId: UUID,
    val nodeTypeIdMap: Map<String, UUID>,
    val linkTypeIdMap: Map<String, UUID>,
    val componentIdMap: Map<String, UUID>,
    val relationIdMap: Map<String, UUID>
)
