package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import java.util.Optional
import java.util.UUID

@Repository
interface DiagramsRepository : JpaRepository<Diagrams, UUID> {

    fun findByModel_IdAndNameAndDeletedFalse(modelId: UUID, name: String): List<Diagrams>

    fun findByModel_IdAndNameAndDeletedFalseOrderByCreatedAtDesc(
        modelId: UUID,
        name: String
    ): List<Diagrams>
    @Query("SELECT d FROM Diagrams d WHERE d.deleted = false")
    override fun findAll(pageable: Pageable): Page<Diagrams>

    @Query("SELECT d FROM Diagrams d WHERE d.id = :id AND d.deleted = false")
    override fun findById(id: UUID): Optional<Diagrams>

    @Query(
        """
        SELECT d FROM Diagrams d
        WHERE d.deleted = false
          AND (:ownerId IS NULL OR d.owner.id = :ownerId)
          AND (:modelId IS NULL OR d.model.id = :modelId)
          AND (:nodeId IS NULL OR d.node.id = :nodeId)
          AND (:notationId IS NULL OR d.notation.id = :notationId)
          AND LOWER(d.name) LIKE LOWER(CONCAT('%', :name, '%'))
        """
    )
    fun findByFilters(
        ownerId: UUID?,
        modelId: UUID?,
        nodeId: UUID?,
        notationId: UUID?,
        name: String,
        pageable: Pageable
    ): Page<Diagrams>

    @Query(
        "SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
            "FROM Diagrams d WHERE d.model = :model AND d.name = :name AND d.version = :version AND d.deleted = false"
    )
    fun existsByModelAndNameAndVersion(model: Models, name: String, version: String): Boolean

    @Query(
        "SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
            "FROM Diagrams d WHERE d.model = :model AND d.name = :name AND d.version = :version AND d.id != :id AND d.deleted = false"
    )
    fun existsByModelAndNameAndVersionAndIdNot(model: Models, name: String, version: String, id: UUID): Boolean

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Diagrams d WHERE d.id = :id AND d.deleted = false")
    override fun existsById(id: UUID): Boolean

    @Modifying
    @Query("UPDATE Diagrams d SET d.deleted = true WHERE d.id = :id")
    fun softDeleteById(id: UUID): Int
}
