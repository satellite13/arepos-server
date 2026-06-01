package ru.kavader.arepos.dto.system

import java.util.UUID

/** Атомарное событие внутри STOMP payload model sync (granular, этап 2). */
data class ModelSyncEntityEvent(
    val type: String,
    val entity: String,
    val id: UUID
)
