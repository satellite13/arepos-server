package ru.kavader.arepos.dto.model

import java.util.UUID

data class AmbiguousNotationCandidate(
    val id: UUID,
    val name: String,
    val version: String
)

class AmbiguousNotationElementException(
    val kind: String,
    val notationId: UUID,
    val query: String,
    val candidates: List<AmbiguousNotationCandidate>
) : RuntimeException("Ambiguous $kind '$query' in notation $notationId (${candidates.size} matches)")

class DiagramConflictException(
    message: String,
    val diagramId: UUID,
    val serverUpdatedAt: java.time.Instant?,
    val clientBaseUpdatedAt: java.time.Instant?
) : RuntimeException(message)
