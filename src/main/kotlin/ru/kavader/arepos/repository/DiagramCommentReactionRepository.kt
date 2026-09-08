package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.DiagramCommentReaction
import ru.kavader.arepos.model.DiagramCommentReactionKey
import java.util.UUID

interface DiagramCommentReactionRepository : JpaRepository<DiagramCommentReaction, DiagramCommentReactionKey> {
    fun findByKeyCommentId(commentId: UUID): List<DiagramCommentReaction>

    fun findByKeyCommentIdIn(commentIds: Collection<UUID>): List<DiagramCommentReaction>

    fun deleteByKey(diagramCommentReactionKey: DiagramCommentReactionKey): Long
}
