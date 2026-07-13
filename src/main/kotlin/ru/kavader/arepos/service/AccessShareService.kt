package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.access.AccessShareRequest
import ru.kavader.arepos.dto.access.AccessShareResponse
import ru.kavader.arepos.mapper.AccessMapper
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ACCESS_DENIED
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class AccessShareService(
    private val resourceSharesRepository: ResourceSharesRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val nodeShapesRepository: NodeShapesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val accessService: ResourceAccessService,
    private val accessMapper: AccessMapper
) {
    @Transactional
    fun grant(request: AccessShareRequest): AccessShareResponse {
        val resourceType = request.resourceType ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "resourceType is required"
        )
        val resourceId = request.resourceId ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "resourceId is required"
        )
        val ownerId = resolveOwnerId(resourceType, resourceId)
        if (!accessService.canManageShares(ownerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
        }
        val currentUser = accessService.currentUserId()
        if (request.granteeUserId == currentUser) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot grant access to yourself")
        }
        val grantee = request.granteeUserId?.let { granteeUserId ->
            usersRepository.findById(granteeUserId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $granteeUserId not found")
            }
        }
        val grantedBy = usersRepository.findById(currentUser).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUser not found")
        }
        val requestedPermission = request.permission ?: SharePermission.VIEW
        val existing = if (request.granteeUserId != null) {
            resourceSharesRepository.findByResourceTypeAndResourceIdAndGranteeUserId(
                resourceType,
                resourceId,
                request.granteeUserId
            )
        } else {
            resourceSharesRepository.findByResourceTypeAndResourceIdAndGranteeUserIsNull(resourceType, resourceId)
        }
        if (existing.isNotEmpty()) {
            val current = existing.first()
            if (current.permission == requestedPermission) {
                return accessMapper.toResponse(current)
            }
            existing.drop(1).forEach { resourceSharesRepository.deleteById(requireNotNull(it.id)) }
            current.permission = requestedPermission
            return accessMapper.toResponse(resourceSharesRepository.save(current))
        }

        val now = Instant.now()
        return accessMapper.toResponse(
            resourceSharesRepository.save(
                ResourceShares(
                    resourceType = resourceType,
                    resourceId = resourceId,
                    granteeUser = grantee,
                    grantedByUser = grantedBy,
                    permission = requestedPermission,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
    }

    private fun resolveOwnerId(resourceType: ShareResourceType, resourceId: UUID): UUID =
        when (resourceType) {
            ShareResourceType.MODEL -> modelsRepository.findById(resourceId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $resourceId not found")
            }.owner.id!!

            ShareResourceType.NOTATION -> notationsRepository.findById(resourceId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $resourceId not found")
            }.owner.id!!

            ShareResourceType.NODE_TYPE -> nodeTypesRepository.findById(resourceId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $resourceId not found")
            }.owner.id!!

            ShareResourceType.LINK_TYPE -> linkTypesRepository.findById(resourceId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $resourceId not found")
            }.owner.id!!

            ShareResourceType.NODE_SHAPE -> nodeShapesRepository.findById(resourceId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $resourceId not found")
            }.owner.id!!
        }
}
