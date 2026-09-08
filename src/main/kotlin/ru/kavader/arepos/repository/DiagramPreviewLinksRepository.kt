package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.DiagramPreviewLinks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import java.util.*

@Repository
interface DiagramPreviewLinksRepository : JpaRepository<DiagramPreviewLinks, UUID> {

    fun findByToken(token: UUID): Optional<DiagramPreviewLinks>

    @Query(
        """
        SELECT l FROM DiagramPreviewLinks l
        LEFT JOIN FETCH l.diagram d
        LEFT JOIN FETCH d.model
        LEFT JOIN FETCH l.model
        WHERE l.token = :token
        """
    )
    fun findByTokenWithTargets(@Param("token") token: UUID): Optional<DiagramPreviewLinks>

    fun findByDiagramAndLatest(diagram: Diagrams, latest: Boolean): Optional<DiagramPreviewLinks>

    @Query(
        """
        SELECT l FROM DiagramPreviewLinks l
        JOIN l.diagram d
        WHERE l.latest = true AND d.seriesId = :seriesId
        """
    )
    fun findByLatestTrueAndDiagramSeriesId(@Param("seriesId") seriesId: UUID): Optional<DiagramPreviewLinks>

    fun findByModelAndDiagramName(model: Models, diagramName: String): Optional<DiagramPreviewLinks>
}
