package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.RoadmapMilestoneItem
import java.util.UUID

interface RoadmapMilestoneItemRepository : JpaRepository<RoadmapMilestoneItem, UUID> {
    fun findByMilestoneId(milestoneId: UUID): List<RoadmapMilestoneItem>
    fun findByFeedbackItemId(feedbackItemId: UUID): List<RoadmapMilestoneItem>
    fun existsByMilestoneIdAndFeedbackItemId(milestoneId: UUID, feedbackItemId: UUID): Boolean
    fun deleteByMilestoneId(milestoneId: UUID)
}
