package ru.kavader.arepos.dto.search

import java.util.UUID

data class CatalogSearchResponse(
    val q: String,
    val limit: Int,
    val totalEstimate: Int,
    val hits: List<CatalogSearchHit>
)

data class CatalogSearchHit(
    val kind: String,
    val id: UUID,
    val name: String,
    val version: String
)

data class ModelSearchResponse(
    val modelId: UUID,
    val q: String,
    val limit: Int,
    val totalEstimate: Int,
    val hits: List<ModelSearchHit>
)

data class ModelSearchHit(
    val kind: String,
    val id: UUID,
    val name: String?,
    val typeName: String? = null,
    val nodeTypeId: UUID? = null,
    val parentId: UUID? = null,
    val pathNames: List<String>? = null,
    val sourceId: UUID? = null,
    val targetId: UUID? = null,
    val sourceName: String? = null,
    val targetName: String? = null,
    val notationName: String? = null
)

data class NotationSearchResponse(
    val notationId: UUID,
    val q: String,
    val limit: Int,
    val totalEstimate: Int,
    val hits: List<NotationSearchHit>
)

data class NotationSearchHit(
    val kind: String,
    val id: UUID,
    val name: String,
    val version: String,
    val nodeTypeId: UUID? = null,
    val linkTypeId: UUID? = null
)
