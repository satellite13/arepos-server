package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.RoadmapMilestone
import java.util.UUID

interface RoadmapMilestoneRepository : JpaRepository<RoadmapMilestone, UUID> {
    fun findAllByOrderBySortOrderAsc(): List<RoadmapMilestone>
}
