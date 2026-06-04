package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.access.AccessMapper
import ru.kavader.arepos.dto.access.AccessShareRequest
import ru.kavader.arepos.dto.access.AccessShareResponse
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/access/shares")
@Tag(name = "Access Shares", description = "Resource sharing and permission grant endpoints")
class AccessSharesController(
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
    @PostMapping
    @Operation(summary = "Grant or update resource share")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun grantShare(@RequestBody request: AccessShareRequest): AccessShareResponse {
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
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
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
                resourceType = resourceType,
                resourceId = resourceId,
                granteeUserId = request.granteeUserId
            )
        } else {
            resourceSharesRepository.findByResourceTypeAndResourceIdAndGranteeUserIsNull(
                resourceType = resourceType,
                resourceId = resourceId
            )
        }
        if (existing.isNotEmpty()) {
            val current = existing.first()
            if (current.permission == requestedPermission) {
                return accessMapper.toResponse(current)
            }

            // Enforce one effective permission per user/resource by replacing stale rows.
            existing.drop(1).forEach { stale -> resourceSharesRepository.deleteById(requireNotNull(stale.id)) }
            current.permission = requestedPermission
            val updated = resourceSharesRepository.save(current)
            return accessMapper.toResponse(updated)
        }
        val now = Instant.now()
        val saved = resourceSharesRepository.save(
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
        return accessMapper.toResponse(saved)
    }

    @GetMapping("/{resourceType}/{resourceId}")
    @Operation(summary = "List shares for resource")
    fun listResourceShares(
        @PathVariable resourceType: ShareResourceType,
        @PathVariable resourceId: UUID
    ): ListResponse<AccessShareResponse> {
        val ownerId = resolveOwnerId(resourceType, resourceId)
        if (!accessService.canManageShares(ownerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
        return resourceSharesRepository.findByResourceTypeAndResourceId(
            resourceType = resourceType,
            resourceId = resourceId
        ).map { accessMapper.toResponse(it) }.toListResponse()
    }

    @DeleteMapping("/{shareId}")
    @Operation(summary = "Revoke share by id")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeShare(@PathVariable shareId: UUID) {
        val share = resourceSharesRepository.findById(shareId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Share $shareId not found")
        }
        val ownerId = resolveOwnerId(share.resourceType, share.resourceId)
        if (!accessService.canManageShares(ownerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
        resourceSharesRepository.deleteById(shareId)
    }

    private fun resolveOwnerId(resourceType: ShareResourceType, resourceId: UUID): UUID {
        return when (resourceType) {
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

}
