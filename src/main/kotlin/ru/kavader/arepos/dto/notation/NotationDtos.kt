package ru.kavader.arepos.dto.notation

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.*

data class NotationRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val version: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NotationUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NotationResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val sourceId: UUID? = null
)

data class NotationMetaResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val ownerEmail: String
)

data class ComponentRequest(
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID,
    val attrs: String? = null
)

data class ComponentUpdateRequest(
    override val name: String? = null,
    override val version: String? = null,
    val notationId: UUID? = null,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID? = null,
    override val attrs: String? = null
) : NotationBoundEntityUpdateRequest

data class ComponentResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID,
    val nodeTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class RelationRequest(
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID? = null,
    val linkTypeId: UUID,
    val attrs: String? = null
)

data class RelationUpdateRequest(
    override val name: String? = null,
    override val version: String? = null,
    val notationId: UUID? = null,
    val ownerId: UUID? = null,
    val linkTypeId: UUID? = null,
    override val attrs: String? = null
) : NotationBoundEntityUpdateRequest

data class RelationResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class NodeTypeRequest(
    val name: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NodeTypeUpdateRequest(
    override val name: String? = null,
    override val ownerId: UUID? = null,
    override val attrs: String? = null
) : CatalogTypeUpdateRequest

data class NodeTypeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class LinkTypeRequest(
    val name: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class LinkTypeUpdateRequest(
    override val name: String? = null,
    override val ownerId: UUID? = null,
    override val attrs: String? = null
) : CatalogTypeUpdateRequest

data class LinkTypeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class RelationRuleRequest(
    val relationId: UUID,
    val fromComponentId: UUID,
    val toComponentId: UUID,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class RelationRuleUpdateRequest(
    val relationId: UUID? = null,
    val fromComponentId: UUID? = null,
    val toComponentId: UUID? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class RelationRuleResponse(
    val id: UUID,
    val relationId: UUID,
    val fromComponentId: UUID,
    val toComponentId: UUID,
    val ownerId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class NodeShapeRequest(
    val name: String,
    val outline: String? = null,
    val contentArea: String? = null,
    val attrs: String? = null
)

data class NodeShapeUpdateRequest(
    val name: String? = null,
    val outline: String? = null,
    val contentArea: String? = null,
    val attrs: String? = null
)

data class NodeShapeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val outline: String?,
    val contentArea: String?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
