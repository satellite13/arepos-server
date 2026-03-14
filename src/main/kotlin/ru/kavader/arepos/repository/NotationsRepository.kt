package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Users
import java.util.Optional
import java.util.UUID

@Repository
interface NotationsRepository : JpaRepository<Notations, UUID> {
    // Методы для получения только неудаленных нотаций
    @Query("SELECT n FROM Notations n WHERE n.deleted = false")
    override fun findAll(pageable: Pageable): Page<Notations>
    
    @Query("SELECT n FROM Notations n WHERE n.id = :id AND n.deleted = false")
    override fun findById(id: UUID): Optional<Notations>
    
    @Query("SELECT n FROM Notations n WHERE n.owner = :owner AND n.deleted = false")
    fun findByOwner(owner: Users, pageable: Pageable): Page<Notations>
    
    @Query("SELECT n FROM Notations n WHERE LOWER(n.name) LIKE LOWER(CONCAT('%', :name, '%')) AND n.deleted = false")
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Notations>
    
    @Query("SELECT n FROM Notations n WHERE n.owner = :owner AND LOWER(n.name) LIKE LOWER(CONCAT('%', :name, '%')) AND n.deleted = false")
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<Notations>

    @Query("SELECT n FROM Notations n WHERE n.source.id = :sourceId AND n.deleted = false")
    fun findBySourceId(sourceId: UUID): List<Notations>

    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM Notations n WHERE n.name = :name AND n.version = :version AND n.deleted = false")
    fun existsByNameAndVersion(name: String, version: String): Boolean

    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM Notations n WHERE n.id = :id AND n.deleted = false")
    override fun existsById(id: UUID): Boolean
    
    @Query("SELECT n FROM Notations n WHERE n.deleted = true")
    fun findByDeletedTrue(pageable: Pageable): Page<Notations>

    @Query("SELECT n FROM Notations n WHERE n.id = :id")
    fun findByIdIncludingDeleted(id: UUID): Optional<Notations>

    // Метод для мягкого удаления
    @Modifying
    @Query("UPDATE Notations n SET n.deleted = true WHERE n.id = :id")
    fun softDeleteById(id: UUID): Int
}


