package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.kavader.arepos.model.Files
import java.util.*

interface FilesRepository : JpaRepository<Files, UUID>
