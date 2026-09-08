package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.kavader.arepos.model.CommentLinkPreview
import java.time.Instant

interface CommentLinkPreviewRepository : JpaRepository<CommentLinkPreview, String> {
    @Query("SELECT p FROM CommentLinkPreview p WHERE p.fetchedAt < :staleBefore")
    fun findStale(staleBefore: Instant): List<CommentLinkPreview>
}
