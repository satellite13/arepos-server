package ru.kavader.arepos.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.kavader.arepos.model.RoadmapMilestone
import java.util.UUID

interface RoadmapMilestoneRepository : JpaRepository<RoadmapMilestone, UUID> {
    fun findAllByOrderBySortOrderAsc(): List<RoadmapMilestone>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select milestone from RoadmapMilestone milestone where milestone.id = :id")
    fun findByIdForUpdate(id: UUID): RoadmapMilestone?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select milestone from RoadmapMilestone milestone where milestone.id in :ids order by milestone.id")
    fun findAllByIdInForUpdate(ids: Collection<UUID>): List<RoadmapMilestone>
}
