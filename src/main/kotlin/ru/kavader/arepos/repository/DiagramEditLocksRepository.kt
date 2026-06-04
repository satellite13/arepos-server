package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.kavader.arepos.model.DiagramEditLocks
import java.time.Instant
import java.util.*

interface DiagramEditLocksRepository : JpaRepository<DiagramEditLocks, UUID> {
    fun findByDiagramId(diagramId: UUID): DiagramEditLocks?

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM DiagramEditLocks l WHERE l.diagram.id = :diagramId")
    fun lockByDiagramIdForUpdate(@Param("diagramId") diagramId: UUID): DiagramEditLocks?

    @Query(
        "SELECT l FROM DiagramEditLocks l JOIN FETCH l.diagram d JOIN FETCH l.lockedBy " +
                "WHERE d.model.id = :modelId AND l.expiresAt >= :now"
    )
    fun findActiveByModelId(@Param("modelId") modelId: UUID, @Param("now") now: Instant): List<DiagramEditLocks>

    @Query(
        "SELECT l FROM DiagramEditLocks l JOIN FETCH l.diagram d JOIN FETCH l.lockedBy " +
                "WHERE l.expiresAt >= :now"
    )
    fun findAllActive(@Param("now") now: Instant): List<DiagramEditLocks>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DiagramEditLocks l WHERE l.expiresAt < :cutoff")
    fun deleteExpiredBefore(@Param("cutoff") cutoff: Instant): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DiagramEditLocks l WHERE l.diagram.id = :diagramId")
    fun deleteByDiagramId(@Param("diagramId") diagramId: UUID): Int

    @Query(
        "SELECT l FROM DiagramEditLocks l JOIN FETCH l.lockedBy JOIN FETCH l.diagram d JOIN FETCH d.model " +
                "WHERE l.diagram.id = :diagramId AND l.expiresAt >= :now"
    )
    fun findActiveWithDiagram(
        @Param("diagramId") diagramId: UUID,
        @Param("now") now: Instant
    ): DiagramEditLocks?
}
