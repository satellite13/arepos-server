package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.kavader.arepos.model.DiagramComment
import java.time.Instant
import java.util.UUID

interface DiagramCommentRepository : JpaRepository<DiagramComment, UUID> {
    fun findByDiagramIdAndDeletedAtIsNull(diagramId: UUID): List<DiagramComment>

    fun findByDiagramIdAndThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(
        diagramId: UUID,
        threadId: UUID
    ): List<DiagramComment>

    @Query(
        """
        SELECT c FROM DiagramComment c
        WHERE c.diagram.id = :diagramId
          AND c.thread IS NULL
          AND c.deletedAt IS NULL
          AND c.isResolved = false
          AND c.targetType IN ('node', 'edge')
          AND c.instanceId IS NOT NULL
          AND c.instanceId NOT IN :existingInstanceIds
        """
    )
    fun findUnresolvedRootsForMissingInstances(
        @Param("diagramId") diagramId: UUID,
        @Param("existingInstanceIds") existingInstanceIds: Collection<String>
    ): List<DiagramComment>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE DiagramComment c
        SET c.isResolved = true,
            c.resolvedReason = 'element_removed',
            c.resolvedAt = :now,
            c.updatedAt = :now
        WHERE c.diagram.id = :diagramId
          AND c.thread IS NULL
          AND c.deletedAt IS NULL
          AND c.isResolved = false
          AND c.targetType IN ('node', 'edge')
          AND c.instanceId IN :removedInstanceIds
        """
    )
    fun resolveByRemovedInstanceIds(
        @Param("diagramId") diagramId: UUID,
        @Param("removedInstanceIds") removedInstanceIds: Collection<String>,
        @Param("now") now: Instant
    ): Int

    fun countByDiagramIdAndDeletedAtIsNull(diagramId: UUID): Long

    @Query(
        """
        SELECT COUNT(DISTINCT c.id) FROM DiagramComment c
        WHERE c.diagram.id = :diagramId
          AND c.deletedAt IS NULL
          AND c.targetType IN ('node', 'edge')
          AND c.instanceId IN :instanceIds
        """
    )
    fun countDistinctByInstanceIds(
        @Param("diagramId") diagramId: UUID,
        @Param("instanceIds") instanceIds: Collection<String>
    ): Long

    fun deleteByDiagramId(diagramId: UUID): Long

    @Query(
        value = """
        SELECT c.* FROM public.comments c
        JOIN public.comments root ON root.id = COALESCE(c.thread_id, c.id)
        JOIN public.models m ON m.id = c.model_id
        JOIN public.diagrams d ON d.id = c.diagram_id
        WHERE c.deleted_at IS NULL
          AND root.deleted_at IS NULL
          AND root.is_resolved = false
          AND (
            m.owner = :userId
            OR EXISTS (
              SELECT 1 FROM public.comments mc
              WHERE mc.diagram_id = c.diagram_id
                AND mc.deleted_at IS NULL
                AND mc.mentions::text LIKE :mentionLike
            )
            OR d.updated_by = :userId
          )
        ORDER BY c.created_at DESC NULLS LAST
        LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findRecentUnresolvedForDiagramsOfAuthorOrMentioned(
        @Param("userId") userId: UUID,
        @Param("mentionLike") mentionLike: String,
        @Param("limit") limit: Int
    ): List<DiagramComment>
}
