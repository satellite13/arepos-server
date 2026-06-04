package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.Users
import java.util.*

@Repository
interface NodeShapesRepository : JpaRepository<NodeShapes, UUID> {
    fun findByOwner(owner: Users, pageable: Pageable): Page<NodeShapes>
}
