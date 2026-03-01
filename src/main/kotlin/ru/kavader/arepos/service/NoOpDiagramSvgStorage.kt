package ru.kavader.arepos.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Service
import java.util.UUID

@Service
@ConditionalOnMissingBean(DiagramSvgStorage::class)
class NoOpDiagramSvgStorage : DiagramSvgStorage {

    override fun putSvg(diagramId: UUID, svgContent: String): Boolean = false

    override fun getSvg(diagramId: UUID): ByteArray? = null
}
