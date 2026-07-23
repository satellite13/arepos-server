package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.kavader.arepos.model.RoadmapMilestoneItem
import java.util.UUID

interface RoadmapMilestoneItemRepository : JpaRepository<RoadmapMilestoneItem, UUID> {
    fun findByMilestoneId(milestoneId: UUID): List<RoadmapMilestoneItem>
    fun findByFeedbackItemId(feedbackItemId: UUID): List<RoadmapMilestoneItem>
    fun existsByMilestoneIdAndFeedbackItemId(milestoneId: UUID, feedbackItemId: UUID): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RoadmapMilestoneItem r WHERE r.milestone.id = :milestoneId")
    fun deleteByMilestoneId(@Param("milestoneId") milestoneId: UUID): Int
}
