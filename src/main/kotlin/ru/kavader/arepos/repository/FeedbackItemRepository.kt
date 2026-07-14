package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.kavader.arepos.model.FeedbackItem
import java.util.UUID

interface FeedbackItemRepository : JpaRepository<FeedbackItem, UUID> {
    @Query(
        """
        SELECT f FROM FeedbackItem f
        WHERE (:type IS NULL OR f.type = :type)
          AND (:status IS NULL OR f.status = :status)
        """
    )
    fun findByFilters(
        @Param("type") type: String?,
        @Param("status") status: String?,
        pageable: Pageable
    ): Page<FeedbackItem>
}
