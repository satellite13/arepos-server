package ru.kavader.arepos.repository

import java.time.Instant
import java.util.UUID

interface RelationRuleListProjection {
    val id: UUID
    val relationId: UUID
    val fromComponentId: UUID
    val toComponentId: UUID
    val ownerId: UUID
    val attrs: String?
    val createdAt: Instant?
    val updatedAt: Instant?
}
