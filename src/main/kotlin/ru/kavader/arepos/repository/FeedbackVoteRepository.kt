package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.FeedbackVote
import java.util.Optional
import java.util.UUID

interface FeedbackVoteRepository : JpaRepository<FeedbackVote, UUID> {
    fun findByItemIdAndUserId(itemId: UUID, userId: UUID): Optional<FeedbackVote>
    fun existsByItemIdAndUserId(itemId: UUID, userId: UUID): Boolean
}
