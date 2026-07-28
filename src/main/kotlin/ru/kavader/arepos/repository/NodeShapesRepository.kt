package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.Users
import java.util.*

@Repository
interface NodeShapesRepository : JpaRepository<NodeShapes, UUID> {
    @Query("SELECT ns FROM NodeShapes ns WHERE ns.deleted = false")
    override fun findAll(pageable: Pageable): Page<NodeShapes>

    @Query("SELECT ns FROM NodeShapes ns WHERE ns.deleted = false")
    override fun findAll(): MutableList<NodeShapes>

    @Query("SELECT ns FROM NodeShapes ns WHERE ns.id = :id AND ns.deleted = false")
    override fun findById(id: UUID): Optional<NodeShapes>

    @Query("SELECT CASE WHEN COUNT(ns) > 0 THEN true ELSE false END FROM NodeShapes ns WHERE ns.id = :id AND ns.deleted = false")
    override fun existsById(id: UUID): Boolean

    @Query(
        """
        SELECT ns FROM NodeShapes ns
        WHERE ns.owner = :owner AND LOWER(ns.name) = LOWER(:name) AND ns.deleted = false
        """
    )
    fun findByOwnerAndNameIgnoreCase(owner: Users, name: String): NodeShapes?

    @Query("SELECT ns FROM NodeShapes ns WHERE ns.owner = :owner AND ns.deleted = false")
    fun findByOwner(owner: Users): List<NodeShapes>

    @Query("SELECT ns FROM NodeShapes ns WHERE ns.owner = :owner AND ns.deleted = false")
    fun findByOwner(owner: Users, pageable: Pageable): Page<NodeShapes>

    @Query("SELECT ns FROM NodeShapes ns WHERE ns.deleted = true")
    fun findByDeletedTrue(pageable: Pageable): Page<NodeShapes>

    @Query("SELECT ns FROM NodeShapes ns WHERE ns.id = :id")
    fun findByIdIncludingDeleted(id: UUID): Optional<NodeShapes>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE NodeShapes ns SET ns.deleted = true WHERE ns.id = :id AND ns.deleted = false")
    fun softDeleteById(id: UUID): Int
}
