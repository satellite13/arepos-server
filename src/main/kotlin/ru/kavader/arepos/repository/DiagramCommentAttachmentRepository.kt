package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.kavader.arepos.model.DiagramCommentAttachment
import java.util.UUID

interface DiagramCommentAttachmentRepository : JpaRepository<DiagramCommentAttachment, UUID> {
    fun findByCommentIdOrderByPositionAscFileIdAsc(commentId: UUID): List<DiagramCommentAttachment>

    fun findByCommentIdIn(commentIds: Collection<UUID>): List<DiagramCommentAttachment>

    fun existsByFileId(fileId: UUID): Boolean

    fun findFirstByFileId(fileId: UUID): DiagramCommentAttachment?

    @Query("SELECT a.file.id FROM DiagramCommentAttachment a WHERE a.comment.id = :commentId")
    fun findFileIdsByCommentId(@Param("commentId") commentId: UUID): List<UUID>

    @Query(
        """
        SELECT a.file.id FROM DiagramCommentAttachment a
        WHERE a.comment.id IN (
            SELECT c.id FROM DiagramComment c WHERE c.diagram.id = :diagramId
        )
        """
    )
    fun findFileIdsByDiagramId(@Param("diagramId") diagramId: UUID): List<UUID>
}
