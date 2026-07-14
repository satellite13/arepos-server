package ru.kavader.arepos.security.access

import org.springframework.stereotype.Component
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.ResourceSharesRepository
import java.util.UUID

data class ShareFlags(val hasView: Boolean, val hasEdit: Boolean)

@Component
class ShareResolver(
    private val resourceSharesRepository: ResourceSharesRepository
) {
    private val viewPermissions = setOf(SharePermission.VIEW, SharePermission.EDIT)

    fun resolveShareFlags(
        resourceType: ShareResourceType,
        resourceId: UUID,
        userId: UUID?
    ): ShareFlags {
        if (userId == null) {
            return ShareFlags(hasView = false, hasEdit = false)
        }

        val hasEdit = resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            granteeUserId = userId,
            permission = SharePermission.EDIT
        ) || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            permission = SharePermission.EDIT
        )

        val hasView =
            hasEdit || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIdAndPermissionIn(
                resourceType = resourceType,
                resourceId = resourceId,
                granteeUserId = userId,
                permissions = viewPermissions
            ) || resourceSharesRepository.existsByResourceTypeAndResourceIdAndGranteeUserIsNullAndPermissionIn(
                resourceType = resourceType,
                resourceId = resourceId,
                permissions = viewPermissions
            )

        return ShareFlags(hasView = hasView, hasEdit = hasEdit)
    }

    fun resolveShareFlagsBatch(
        resourceType: ShareResourceType,
        resourceIds: Set<UUID>,
        userId: UUID?
    ): Map<UUID, ShareFlags> {
        if (resourceIds.isEmpty() || userId == null) {
            return resourceIds.associateWith { ShareFlags(hasView = false, hasEdit = false) }
        }

        val userShares = resourceSharesRepository.findByResourceTypeAndResourceIdInAndGranteeUserIdAndPermissionIn(
            resourceType = resourceType,
            resourceIds = resourceIds,
            granteeUserId = userId,
            permissions = viewPermissions
        )
        val publicShares =
            resourceSharesRepository.findByResourceTypeAndResourceIdInAndGranteeUserIsNullAndPermissionIn(
                resourceType = resourceType,
                resourceIds = resourceIds,
                permissions = viewPermissions
            )

        val resolved = resourceIds.associateWith {
            ShareFlags(hasView = false, hasEdit = false)
        }.toMutableMap()
        (userShares + publicShares).forEach { share ->
            val previous = resolved[share.resourceId] ?: ShareFlags(hasView = false, hasEdit = false)
            resolved[share.resourceId] = ShareFlags(
                hasView = true,
                hasEdit = previous.hasEdit || share.permission == SharePermission.EDIT
            )
        }
        return resolved
    }
}
