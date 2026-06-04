package ru.kavader.arepos.repository

import java.time.Instant
import java.util.*

interface RelationRuleListLightProjection {
    val id: UUID
    val relationId: UUID
    val fromComponentId: UUID
    val toComponentId: UUID
    val ownerId: UUID
    val createdAt: Instant?
    val updatedAt: Instant?
}
