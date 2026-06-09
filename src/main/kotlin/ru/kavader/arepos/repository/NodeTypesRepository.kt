package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.Users
import java.util.*

@Repository
interface NodeTypesRepository : JpaRepository<NodeTypes, UUID> {
    fun findByOwner(owner: Users, pageable: Pageable): Page<NodeTypes>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<NodeTypes>
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<NodeTypes>
    fun findByNameIgnoreCase(name: String): NodeTypes?

    @Query(
        """
        SELECT nt
        FROM NodeTypes nt
        WHERE (:ownerId IS NULL OR nt.owner.id = :ownerId)
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
        WHERE (
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

    fun findByIdIn(ids: Collection<UUID>): List<NodeTypes>
    fun findByOwnerIdIn(ownerIds: Collection<UUID>): List<NodeTypes>
}


