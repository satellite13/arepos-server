package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import java.util.Optional
import java.util.UUID

@Repository
interface ModelsRepository : JpaRepository<Models, UUID> {
    // Методы для получения только неудаленных моделей
    @Query("SELECT m FROM Models m WHERE m.deleted = false")
    override fun findAll(pageable: Pageable): Page<Models>
    
    @Query("SELECT m FROM Models m WHERE m.id = :id AND m.deleted = false")
    override fun findById(id: UUID): Optional<Models>
    
    @Query("SELECT m FROM Models m WHERE m.owner = :owner AND m.deleted = false")
    fun findByOwner(owner: Users, pageable: Pageable): Page<Models>
    
    @Query("SELECT m FROM Models m WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%')) AND m.deleted = false")
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Models>
    
    @Query("SELECT m FROM Models m WHERE m.owner = :owner AND LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%')) AND m.deleted = false")
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<Models>
    
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Models m WHERE m.name = :name AND m.version = :version AND m.deleted = false")
    fun existsByNameAndVersion(name: String, version: String): Boolean
    
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Models m WHERE m.name = :name AND m.version = :version AND m.id != :id AND m.deleted = false")
    fun existsByNameAndVersionAndIdNot(name: String, version: String, id: UUID): Boolean
    
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Models m WHERE m.id = :id AND m.deleted = false")
    override fun existsById(id: UUID): Boolean
    
    // Метод для мягкого удаления
    @Modifying
    @Query("UPDATE Models m SET m.deleted = true WHERE m.id = :id")
    fun softDeleteById(id: UUID): Int
}


