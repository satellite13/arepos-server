package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.FeedbackComment
import java.util.UUID

interface FeedbackCommentRepository : JpaRepository<FeedbackComment, UUID> {
    fun findByItemIdOrderByCreatedAtAsc(itemId: UUID): List<FeedbackComment>
}
