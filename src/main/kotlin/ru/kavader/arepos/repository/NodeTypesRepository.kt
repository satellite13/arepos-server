package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.Users
import java.util.*

@Repository
interface NodeTypesRepository : JpaRepository<NodeTypes, UUID> {
    @Query("SELECT nt FROM NodeTypes nt WHERE nt.deleted = false")
    override fun findAll(pageable: Pageable): Page<NodeTypes>

    @Query("SELECT nt FROM NodeTypes nt WHERE nt.id = :id AND nt.deleted = false")
    override fun findById(id: UUID): Optional<NodeTypes>

    @Query("SELECT CASE WHEN COUNT(nt) > 0 THEN true ELSE false END FROM NodeTypes nt WHERE nt.id = :id AND nt.deleted = false")
    override fun existsById(id: UUID): Boolean

    @Query("SELECT nt FROM NodeTypes nt WHERE nt.owner = :owner AND nt.deleted = false")
    fun findByOwner(owner: Users, pageable: Pageable): Page<NodeTypes>

    @Query(
        """
        SELECT nt FROM NodeTypes nt
        WHERE LOWER(nt.name) LIKE LOWER(CONCAT('%', :name, '%')) AND nt.deleted = false
        """
    )
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<NodeTypes>

    @Query(
        """
        SELECT nt FROM NodeTypes nt
        WHERE nt.owner = :owner
          AND LOWER(nt.name) LIKE LOWER(CONCAT('%', :name, '%'))
          AND nt.deleted = false
        """
    )
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<NodeTypes>

    @Query("SELECT nt FROM NodeTypes nt WHERE LOWER(nt.name) = LOWER(:name) AND nt.deleted = false")
    fun findByNameIgnoreCase(name: String): NodeTypes?

    @Query(
        """
        SELECT nt FROM NodeTypes nt
        WHERE nt.owner = :owner AND LOWER(nt.name) = LOWER(:name) AND nt.deleted = false
        """
    )
    fun findByOwnerAndNameIgnoreCase(owner: Users, name: String): NodeTypes?

    @Query(
        """
        SELECT nt FROM NodeTypes nt
        WHERE LOWER(nt.owner.email) = LOWER(:ownerEmail)
          AND LOWER(nt.name) = LOWER(:name)
          AND nt.deleted = false
        """
    )
    fun findByOwnerEmailIgnoreCaseAndNameIgnoreCase(ownerEmail: String, name: String): NodeTypes?

    @Query(
        """
        SELECT nt
        FROM NodeTypes nt
        WHERE nt.deleted = false
          AND (:ownerId IS NULL OR nt.owner.id = :ownerId)
          AND (:name = '' OR LOWER(nt.name) LIKE LOWER(CONCAT('%', :name, '%')))
          AND (
              nt.owner.id = :userId
              OR EXISTS (
                  SELECT rs.id
                  FROM ResourceShares rs
                  WHERE rs.resourceType = ru.kavader.arepos.model.ShareResourceType.NODE_TYPE
                    AND rs.resourceId = nt.id
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
    ): Page<NodeTypes>

    @Query(
        """
        SELECT COUNT(nt)
        FROM NodeTypes nt
        WHERE nt.deleted = false
          AND (
            nt.owner.id = :userId
            OR EXISTS (
                SELECT rs.id
                FROM ResourceShares rs
                WHERE rs.resourceType = ru.kavader.arepos.model.ShareResourceType.NODE_TYPE
                  AND rs.resourceId = nt.id
                  AND rs.permission IN :viewPermissions
                  AND (rs.granteeUser.id = :userId OR rs.granteeUser IS NULL)
            )
        )
        """
    )
    fun countAccessibleForUser(
        userId: UUID,
        viewPermissions: Collection<SharePermission>
    ): Long

    @Query("SELECT COUNT(nt) FROM NodeTypes nt WHERE nt.deleted = false")
    fun countActive(): Long

    /** Includes soft-deleted rows (needed when resolving types still referenced by components/nodes). */
    fun findByIdIn(ids: Collection<UUID>): List<NodeTypes>

    @Query("SELECT nt FROM NodeTypes nt WHERE nt.owner.id IN :ownerIds AND nt.deleted = false")
    fun findByOwnerIdIn(ownerIds: Collection<UUID>): List<NodeTypes>

    @Query("SELECT nt FROM NodeTypes nt WHERE nt.deleted = true")
    fun findByDeletedTrue(pageable: Pageable): Page<NodeTypes>

    @Query("SELECT nt FROM NodeTypes nt WHERE nt.id = :id")
    fun findByIdIncludingDeleted(id: UUID): Optional<NodeTypes>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE NodeTypes nt SET nt.deleted = true WHERE nt.id = :id AND nt.deleted = false")
    fun softDeleteById(id: UUID): Int
}
