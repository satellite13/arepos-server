package ru.kavader.arepos.dto.system

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.util.*

data class RelationRulesSyncRequest(
    @field:Valid
    @field:NotNull
    val rules: List<RelationRuleSyncItem>
)

data class RelationRuleSyncItem(
    @field:NotNull val fromComponentId: UUID,
    @field:NotNull val toComponentId: UUID,
    @field:NotNull val allowedRelationIds: List<UUID>
)

data class RelationRulesSyncResponse(
    val created: Int,
    val deleted: Int,
    val total: Int
)
