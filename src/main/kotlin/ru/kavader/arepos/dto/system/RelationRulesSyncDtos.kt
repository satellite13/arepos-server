package ru.kavader.arepos.dto.system

import java.util.*

data class RelationRulesSyncRequest(
    val rules: List<RelationRuleSyncItem>
)

data class RelationRuleSyncItem(
    val fromComponentId: UUID,
    val toComponentId: UUID,
    val allowedRelationIds: List<UUID>
)

data class RelationRulesSyncResponse(
    val created: Int,
    val deleted: Int,
    val total: Int
)
