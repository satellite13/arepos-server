package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.Users
import java.util.*

@Repository
interface LinkTypesRepository : JpaRepository<LinkTypes, UUID> {
    @Query("SELECT lt FROM LinkTypes lt WHERE lt.deleted = false")
    override fun findAll(pageable: Pageable): Page<LinkTypes>

    @Query("SELECT lt FROM LinkTypes lt WHERE lt.id = :id AND lt.deleted = false")
    override fun findById(id: UUID): Optional<LinkTypes>

    @Query("SELECT CASE WHEN COUNT(lt) > 0 THEN true ELSE false END FROM LinkTypes lt WHERE lt.id = :id AND lt.deleted = false")
    override fun existsById(id: UUID): Boolean

    @Query("SELECT lt FROM LinkTypes lt WHERE lt.owner = :owner AND lt.deleted = false")
    fun findByOwner(owner: Users, pageable: Pageable): Page<LinkTypes>

    @Query(
        """
        SELECT lt FROM LinkTypes lt
        WHERE LOWER(lt.name) LIKE LOWER(CONCAT('%', :name, '%')) AND lt.deleted = false
        """
    )
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<LinkTypes>

    @Query(
        """
        SELECT lt FROM LinkTypes lt
        WHERE lt.owner = :owner
          AND LOWER(lt.name) LIKE LOWER(CONCAT('%', :name, '%'))
          AND lt.deleted = false
        """
    )
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<LinkTypes>

    @Query("SELECT lt FROM LinkTypes lt WHERE LOWER(lt.name) = LOWER(:name) AND lt.deleted = false")
    fun findByNameIgnoreCase(name: String): LinkTypes?

    @Query(
        """
        SELECT lt FROM LinkTypes lt
        WHERE lt.owner = :owner AND LOWER(lt.name) = LOWER(:name) AND lt.deleted = false
        """
    )
    fun findByOwnerAndNameIgnoreCase(owner: Users, name: String): LinkTypes?

    @Query(
        """
        SELECT lt
        FROM LinkTypes lt
        WHERE lt.deleted = false
          AND (:ownerId IS NULL OR lt.owner.id = :ownerId)
          AND (:name = '' OR LOWER(lt.name) LIKE LOWER(CONCAT('%', :name, '%')))
          AND (
              lt.owner.id = :userId
              OR EXISTS (
                  SELECT rs.id
                  FROM ResourceShares rs
                  WHERE rs.resourceType = ru.kavader.arepos.model.ShareResourceType.LINK_TYPE
                    AND rs.resourceId = lt.id
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
    ): Page<LinkTypes>

    @Query(
        """
        SELECT COUNT(lt)
        FROM LinkTypes lt
        WHERE lt.deleted = false
          AND (
            lt.owner.id = :userId
            OR EXISTS (
                SELECT rs.id
                FROM ResourceShares rs
                WHERE rs.resourceType = ru.kavader.arepos.model.ShareResourceType.LINK_TYPE
                  AND rs.resourceId = lt.id
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

    /** Includes soft-deleted rows (needed when resolving types still referenced by relations/links). */
    fun findByIdIn(ids: Collection<UUID>): List<LinkTypes>

    @Query("SELECT lt FROM LinkTypes lt WHERE lt.owner.id IN :ownerIds AND lt.deleted = false")
    fun findByOwnerIdIn(ownerIds: Collection<UUID>): List<LinkTypes>

    @Query("SELECT lt FROM LinkTypes lt WHERE lt.deleted = true")
    fun findByDeletedTrue(pageable: Pageable): Page<LinkTypes>

    @Query("SELECT lt FROM LinkTypes lt WHERE lt.id = :id")
    fun findByIdIncludingDeleted(id: UUID): Optional<LinkTypes>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LinkTypes lt SET lt.deleted = true WHERE lt.id = :id AND lt.deleted = false")
    fun softDeleteById(id: UUID): Int
}
