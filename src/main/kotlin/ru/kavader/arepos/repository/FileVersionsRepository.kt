package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.FileVersions
import ru.kavader.arepos.model.Files
import java.util.*

@Repository
interface FileVersionsRepository : JpaRepository<FileVersions, UUID> {
    fun findByFileOrderByVersionNumberDesc(file: Files): List<FileVersions>
    fun findByFileAndVersionNumber(file: Files, versionNumber: Int): FileVersions?
    fun findTopByFileOrderByVersionNumberDesc(file: Files): FileVersions?
}
