package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import java.util.*

@Repository
interface ResourceSharesRepository : JpaRepository<ResourceShares, UUID> {
    fun existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermission(
        resourceType: ShareResourceType,
        resourceId: UUID,
        granteeUserId: UUID,
        permission: SharePermission
    ): Boolean

    fun findByResourceTypeAndResourceIdInAndGranteeUserIdAndPermissionIn(
        resourceType: ShareResourceType,
        resourceIds: Collection<UUID>,
        granteeUserId: UUID,
        permissions: Collection<SharePermission>
    ): List<ResourceShares>

    fun findByResourceTypeAndResourceIdInAndGranteeUserIsNullAndPermissionIn(
        resourceType: ShareResourceType,
        resourceIds: Collection<UUID>,
        permissions: Collection<SharePermission>
    ): List<ResourceShares>

    fun findByResourceTypeAndResourceId(resourceType: ShareResourceType, resourceId: UUID): List<ResourceShares>

    fun findByResourceTypeAndResourceIdAndGranteeUserId(
        resourceType: ShareResourceType,
        resourceId: UUID,
        granteeUserId: UUID
    ): List<ResourceShares>

    fun findByResourceTypeAndResourceIdAndGranteeUserIsNull(
        resourceType: ShareResourceType,
        resourceId: UUID
    ): List<ResourceShares>

    fun existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermissionIn(
        resourceType: ShareResourceType,
        resourceId: UUID,
        granteeUserId: UUID,
        permissions: Collection<SharePermission>
    ): Boolean

    fun existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermissionIn(
        resourceType: ShareResourceType,
        resourceId: UUID,
        permissions: Collection<SharePermission>
    ): Boolean

    fun existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermission(
        resourceType: ShareResourceType,
        resourceId: UUID,
        permission: SharePermission
    ): Boolean

}
