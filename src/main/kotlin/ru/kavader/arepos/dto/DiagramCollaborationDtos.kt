package ru.kavader.arepos.dto

import java.util.UUID

data class DiagramPointerRequest(
    val worldX: Double,
    val worldY: Double,
    val visible: Boolean = true
)

data class DiagramSpectatorView(
    val userId: UUID,
    val displayName: String
)
