package ru.kavader.arepos.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.*

@Service
@ConditionalOnProperty(name = ["arepos.files.storage"], havingValue = "disabled", matchIfMissing = true)
class NoOpDiagramSvgStorage : DiagramSvgStorage {

    override fun putSvg(diagramId: UUID, svgContent: String): DiagramSvgWriteResult = DiagramSvgWriteResult.Unavailable

    override fun getSvg(diagramId: UUID): DiagramSvgReadResult = DiagramSvgReadResult.NotFound
}
