package ru.kavader.arepos.dto

import java.time.Instant
import java.util.UUID

data class DiagramLockStatusResponse(
    val diagramId: UUID,
    val isLocked: Boolean,
    val lockedByUserId: UUID? = null,
    val lockedByDisplay: String? = null,
    val expiresAt: Instant? = null,
    val diagramUpdatedAt: Instant? = null,
    val reason: String? = null
)
