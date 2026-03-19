package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.SharePermission
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

    @Query("SELECT m FROM Models m WHERE m.name = :name AND m.deleted = false")
    fun findByNameAndDeletedFalse(name: String): List<Models>

    @Query("SELECT m FROM Models m WHERE m.source.id = :sourceId AND m.deleted = false")
    fun findBySourceIdAndDeletedFalse(sourceId: UUID): List<Models>
    
    @Query("SELECT m FROM Models m WHERE m.owner = :owner AND LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%')) AND m.deleted = false")
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<Models>
    
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Models m WHERE m.name = :name AND m.version = :version AND m.deleted = false")
    fun existsByNameAndVersion(name: String, version: String): Boolean
    
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Models m WHERE m.name = :name AND m.version = :version AND m.id != :id AND m.deleted = false")
    fun existsByNameAndVersionAndIdNot(name: String, version: String, id: UUID): Boolean
    
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Models m WHERE m.id = :id AND m.deleted = false")
    override fun existsById(id: UUID): Boolean
    
    @Query("SELECT m FROM Models m WHERE m.deleted = true")
    fun findByDeletedTrue(pageable: Pageable): Page<Models>

    @Query("SELECT m FROM Models m WHERE m.id = :id")
    fun findByIdIncludingDeleted(id: UUID): Optional<Models>

    @Query(
        """
        SELECT m
        FROM Models m
        WHERE m.deleted = false
          AND (:ownerId IS NULL OR m.owner.id = :ownerId)
          AND (:name = '' OR LOWER(m.name) LIKE LOWER(CONCAT('%', :name, '%')))
          AND (
            m.owner.id = :userId
            OR EXISTS (
                SELECT rs.id
                FROM ResourceShares rs
                WHERE rs.resourceType = ru.kavader.arepos.model.ShareResourceType.MODEL
                  AND rs.resourceId = m.id
                  AND rs.permission IN :viewPermissions
                  AND (rs.granteeUser.id = :userId OR rs.granteeUser IS NULL)
            )
          )
        """
    )
    fun findAccessibleForUser(
        userId: UUID,
        ownerId: UUID?,
        name: String,
        viewPermissions: Collection<SharePermission>,
        pageable: Pageable
    ): Page<Models>

    @Query(
        """
        SELECT COUNT(DISTINCT m.name)
        FROM Models m
        WHERE m.deleted = false
          AND (
            m.owner.id = :userId
            OR EXISTS (
                SELECT rs.id
                FROM ResourceShares rs
                WHERE rs.resourceType = ru.kavader.arepos.model.ShareResourceType.MODEL
                  AND rs.resourceId = m.id
                  AND rs.permission IN :viewPermissions
                  AND (rs.granteeUser.id = :userId OR rs.granteeUser IS NULL)
            )
          )
        """
    )
    fun countDistinctAccessibleNamesForUser(
        userId: UUID,
        viewPermissions: Collection<SharePermission>
    ): Long

    @Query("SELECT COUNT(DISTINCT m.name) FROM Models m WHERE m.deleted = false")
    fun countDistinctNamesUndeleted(): Long

    @Query(
        """
        SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
        FROM Models m
        WHERE m.deleted = false
          AND m.owner.id = :ownerId
          AND (
            m.owner.id = :userId
            OR EXISTS (
                SELECT rs.id
                FROM ResourceShares rs
                WHERE rs.resourceType = ru.kavader.arepos.model.ShareResourceType.MODEL
                  AND rs.resourceId = m.id
                  AND rs.permission IN :viewPermissions
                  AND (rs.granteeUser.id = :userId OR rs.granteeUser IS NULL)
            )
          )
        """
    )
    fun existsAccessibleByOwnerForUser(
        ownerId: UUID,
        userId: UUID,
        viewPermissions: Collection<SharePermission>
    ): Boolean

    // Метод для мягкого удаления
    @Modifying
    @Query("UPDATE Models m SET m.deleted = true WHERE m.id = :id")
    fun softDeleteById(id: UUID): Int
}


