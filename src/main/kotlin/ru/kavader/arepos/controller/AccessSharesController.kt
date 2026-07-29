package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.access.AccessShareRequest
import ru.kavader.arepos.dto.access.AccessShareResponse
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.mapper.AccessMapper
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ACCESS_DENIED
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.AccessShareService
import java.util.*

@RestController
@RequestMapping("/api/v1/access/shares")
@Tag(name = "Access Shares", description = "Resource sharing and permission grant endpoints")
class AccessSharesController(
    private val resourceSharesRepository: ResourceSharesRepository,
    private val modelsRepository: ModelsRepository,
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val nodeShapesRepository: NodeShapesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val validationScriptsRepository: ValidationScriptsRepository,
    private val accessService: ResourceAccessService,
    private val accessMapper: AccessMapper,
    private val accessShareService: AccessShareService
) {
    @PostMapping
    @Operation(summary = "Grant or update resource share")
    @ResponseStatus(HttpStatus.CREATED)
    fun grantShare(@RequestBody @Valid request: AccessShareRequest): AccessShareResponse =
        accessShareService.grant(request)

    @GetMapping("/{resourceType}/{resourceId}")
    @Operation(summary = "List shares for resource")
    fun listResourceShares(
        @PathVariable resourceType: ShareResourceType,
        @PathVariable resourceId: UUID
    ): ListResponse<AccessShareResponse> {
        val ownerId = resolveOwnerId(resourceType, resourceId)
        if (!accessService.canManageShares(ownerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
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
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
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

            ShareResourceType.VALIDATION_SCRIPT -> validationScriptsRepository.findById(resourceId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "ValidationScript $resourceId not found")
            }.owner.id!!
        }
    }

}
