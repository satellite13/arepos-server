package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Models
import java.util.UUID

@Repository
interface ModelsRepository : JpaRepository<Models, UUID>


