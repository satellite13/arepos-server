package ru.kavader.arepos.controller

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/access/shares")
class AccessSharesController(
    private val resourceSharesRepository: ResourceSharesRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val accessService: ResourceAccessService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun grantShare(@RequestBody request: AccessShareRequest): AccessShareResponse {
        val ownerId = resolveOwnerId(request.resourceType, request.resourceId)
        if (!accessService.canManageShares(ownerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
        val currentUser = accessService.currentUserId()
        if (request.granteeUserId == currentUser) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot grant access to yourself")
        }
        val grantee = usersRepository.findById(request.granteeUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User ${request.granteeUserId} not found")
        }
        val grantedBy = usersRepository.findById(currentUser).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUser not found")
        }
        val existing = resourceSharesRepository.findByResourceTypeAndResourceIdAndGranteeUserIdAndPermission(
            resourceType = request.resourceType,
            resourceId = request.resourceId,
            granteeUserId = request.granteeUserId,
            permission = SharePermission.EDIT
        )
        if (existing != null) {
            return existing.toResponse()
        }
        val now = Instant.now()
        val saved = resourceSharesRepository.save(
            ResourceShares(
                resourceType = request.resourceType,
                resourceId = request.resourceId,
                granteeUser = grantee,
                grantedByUser = grantedBy,
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )
        return saved.toResponse()
    }

    @GetMapping("/{resourceType}/{resourceId}")
    fun listResourceShares(
        @PathVariable resourceType: ShareResourceType,
        @PathVariable resourceId: UUID
    ): List<AccessShareResponse> {
        val ownerId = resolveOwnerId(resourceType, resourceId)
        if (!accessService.canManageShares(ownerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
        return resourceSharesRepository.findByResourceTypeAndResourceIdAndPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            permission = SharePermission.EDIT
        ).map { it.toResponse() }
    }

    @DeleteMapping("/{shareId}")
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
        }
    }

    private fun ResourceShares.toResponse() = AccessShareResponse(
        id = id!!,
        resourceType = resourceType,
        resourceId = resourceId,
        granteeUserId = granteeUser.id!!,
        grantedByUserId = grantedByUser.id!!,
        permission = permission.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class AccessShareRequest(
    val resourceType: ShareResourceType,
    val resourceId: UUID,
    val granteeUserId: UUID
)

data class AccessShareResponse(
    val id: UUID,
    val resourceType: ShareResourceType,
    val resourceId: UUID,
    val granteeUserId: UUID,
    val grantedByUserId: UUID,
    val permission: String,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
