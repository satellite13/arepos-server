package ru.kavader.arepos.dto.document

import java.util.*

data class DocumentItem(
    val fileId: UUID,
    val label: String,
    val entityType: String? = null,
    val entityId: UUID? = null,
    val entityName: String? = null,
    val parentName: String? = null
)

data class RegisterDocumentRefRequest(
    val fileId: UUID,
    val modelId: UUID? = null,
    val notationId: UUID? = null,
    val componentId: UUID? = null,
    val nodeId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val linkTypeId: UUID? = null,
    val diagramId: UUID? = null,
    val relationId: UUID? = null,
    val nodeShapeId: UUID? = null
)
