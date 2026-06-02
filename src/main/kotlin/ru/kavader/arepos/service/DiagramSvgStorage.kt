package ru.kavader.arepos.service

import java.util.UUID

sealed interface DiagramSvgReadResult {
    data class Found(val bytes: ByteArray) : DiagramSvgReadResult
    data object NotFound : DiagramSvgReadResult
    data class StorageError(val message: String? = null) : DiagramSvgReadResult
}

sealed interface DiagramSvgWriteResult {
    data object Written : DiagramSvgWriteResult
    data object Unavailable : DiagramSvgWriteResult
    data class StorageError(val message: String? = null) : DiagramSvgWriteResult
}

/**
 * Storage for diagram SVG previews (e.g. in MinIO).
 * When MinIO is disabled, a no-op implementation is used.
 */
interface DiagramSvgStorage {

    fun putSvg(diagramId: UUID, svgContent: String): DiagramSvgWriteResult

    fun getSvg(diagramId: UUID): DiagramSvgReadResult
}
