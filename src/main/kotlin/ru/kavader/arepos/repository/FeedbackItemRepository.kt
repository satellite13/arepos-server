package ru.kavader.arepos.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.kavader.arepos.model.FeedbackItem
import java.util.UUID

interface FeedbackItemRepository : JpaRepository<FeedbackItem, UUID> {
    fun findByPublicNumber(publicNumber: Int): FeedbackItem?

    @Query(value = "SELECT nextval('feedback_items_public_number_seq')", nativeQuery = true)
    fun nextPublicNumber(): Long

    @Query(
        """
        SELECT f FROM FeedbackItem f
        WHERE (:type IS NULL OR f.type = :type)
          AND (:status IS NULL OR f.status = :status)
          AND (
            :q IS NULL
            OR (
              :exactPublicNumber IS NOT NULL AND f.publicNumber = :exactPublicNumber
            )
            OR (
              :exactPublicNumber IS NULL
              AND (
                LOWER(f.title) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%') ESCAPE '\'
                OR LOWER(f.body) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%') ESCAPE '\'
              )
            )
          )
        """
    )
    fun findByFilters(
        @Param("type") type: String?,
        @Param("status") status: String?,
        @Param("q") q: String?,
        @Param("exactPublicNumber") exactPublicNumber: Int?,
        pageable: Pageable
    ): Page<FeedbackItem>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FeedbackItem f WHERE f.id IN :ids ORDER BY f.id")
    fun findAllByIdInForUpdate(@Param("ids") ids: Collection<UUID>): List<FeedbackItem>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FeedbackItem f WHERE f.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): FeedbackItem?

    fun existsByMergedIntoId(mergedIntoId: UUID): Boolean
}
