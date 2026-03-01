package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.DiagramPreviewLinks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import java.util.Optional
import java.util.UUID

@Repository
interface DiagramPreviewLinksRepository : JpaRepository<DiagramPreviewLinks, UUID> {

    fun findByToken(token: UUID): Optional<DiagramPreviewLinks>

    fun findByDiagram(diagram: Diagrams): Optional<DiagramPreviewLinks>

    fun findByModelAndDiagramName(model: Models, diagramName: String): Optional<DiagramPreviewLinks>
}
