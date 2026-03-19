package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.Users
import java.util.UUID

@Repository
interface LinkTypesRepository : JpaRepository<LinkTypes, UUID> {
    fun findByOwner(owner: Users, pageable: Pageable): Page<LinkTypes>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<LinkTypes>
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<LinkTypes>
    fun findByNameIgnoreCase(name: String): LinkTypes?

    @Query(
        """
        SELECT lt
        FROM LinkTypes lt
        WHERE (:ownerId IS NULL OR lt.owner.id = :ownerId)
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

    fun findByIdIn(ids: Collection<UUID>): List<LinkTypes>
    fun findByOwnerIdIn(ownerIds: Collection<UUID>): List<LinkTypes>
}


