package ru.kavader.arepos.dto.notation

import java.time.Instant
import java.util.UUID

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
