package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.TutorialVideo
import java.util.UUID

interface TutorialVideoRepository : JpaRepository<TutorialVideo, UUID> {
    fun findByPublishedTrueOrderBySortOrderAsc(): List<TutorialVideo>
    fun findAllByOrderBySortOrderAsc(): List<TutorialVideo>
}
