package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Components
import java.util.UUID

@Repository
interface ComponentsRepository : JpaRepository<Components, UUID>


