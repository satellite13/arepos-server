package ru.kavader.arepos.dto.oef

import java.time.Instant
import java.util.UUID

/**
 * Persisted duplicate-merge decision for OEF imports: the OEF entity
 * [oefEntityId] was merged into node [targetNodeId] during validation.
 */
data class OefMergeDecisionDto(
    val id: UUID,
    val oefEntityId: String,
    val targetNodeId: UUID,
    val signatureType: String?,
    val signatureName: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class OefMergeDecisionSaveRequest(
    val decisions: List<OefMergeDecisionSaveItem> = emptyList(),
)

data class OefMergeDecisionSaveItem(
    val oefEntityId: String = "",
    val targetNodeId: UUID,
    val signatureType: String? = null,
    val signatureName: String? = null,
)

data class OefMergeDecisionSaveResponse(
    val saved: Int,
    val skipped: Int,
)
