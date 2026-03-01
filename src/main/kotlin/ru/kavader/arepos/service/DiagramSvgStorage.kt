package ru.kavader.arepos.service

import java.util.UUID

/**
 * Storage for diagram SVG previews (e.g. in MinIO).
 * When MinIO is disabled, a no-op implementation is used.
 */
interface DiagramSvgStorage {

    fun putSvg(diagramId: UUID, svgContent: String): Boolean

    fun getSvg(diagramId: UUID): ByteArray?
}
