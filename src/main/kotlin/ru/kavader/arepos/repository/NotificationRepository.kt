package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.kavader.arepos.model.Notification
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<Notification>

    fun countByUserIdAndReadAtIsNull(userId: UUID): Long

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.readAt IS NULL")
    fun markAllReadByUserId(userId: UUID): Int
}
