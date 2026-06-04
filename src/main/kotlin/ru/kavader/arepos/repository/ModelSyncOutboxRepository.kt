package ru.kavader.arepos.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.ModelSyncOutbox
import java.time.Instant
import java.util.*

@Repository
interface ModelSyncOutboxRepository : JpaRepository<ModelSyncOutbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT o FROM ModelSyncOutbox o
        WHERE o.publishedAt IS NULL
        ORDER BY o.createdAt ASC
        """
    )
    fun findPendingForPublish(pageable: Pageable): List<ModelSyncOutbox>

    fun countByPublishedAtIsNull(): Long

    @Query("SELECT MIN(o.createdAt) FROM ModelSyncOutbox o WHERE o.publishedAt IS NULL")
    fun findOldestPendingCreatedAt(): Instant?
}
