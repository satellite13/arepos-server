package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.DownloadAsset
import java.util.UUID

interface DownloadAssetRepository : JpaRepository<DownloadAsset, UUID> {
    fun findByPublishedTrueOrderBySortOrderAsc(): List<DownloadAsset>
    fun findAllByOrderBySortOrderAsc(): List<DownloadAsset>
}
