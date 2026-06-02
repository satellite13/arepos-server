package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import ru.kavader.arepos.dto.notation.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/relations")
@Tag(name = "Relations", description = "Notation relations management endpoints")
class RelationsController(
    private val relationsRepository: RelationsRepository,
    private val notationsRepository: NotationsRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val typeUsageAuthorization: TypeUsageAuthorization,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val notationMapper: NotationMapper
) {

    @GetMapping
    @Operation(summary = "List relations")
    fun listRelations(
        pageable: Pageable,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) tagsAll: String?
    ): Page<RelationResponse> =
        NotationBoundEntityListSupport.list(
            accessService = accessService,
            pageable = pageable,
            notationId = notationId,
            modelId = modelId,
            ownerId = ownerId,
            name = name,
            tagsAll = tagsAll,
            findAccessibleForUser = relationsRepository::findAccessibleByFiltersForUser,
            findByFilters = relationsRepository::findByFilters
        ).map { notationMapper.toResponse(it) }

    @GetMapping("/{id}")
    @Operation(summary = "Get relation by id")
    fun getRelation(@PathVariable id: UUID): RelationResponse =
        relationsRepository.findById(id)
            .map {
                accessService.requireCanViewRelation(it)
                notationMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $id not found")
            }

    @PostMapping
    @Operation(summary = "Create relation")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRelation(@RequestBody request: RelationRequest): RelationResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        accessService.requireCanEditNotation(notation)
        val linkType = linkTypesRepository.findById(request.linkTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType ${request.linkTypeId} not found")
            }
        typeUsageAuthorization.requireCanUseLinkTypeForNotation(linkType, notation)
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = relationsRepository.save(
            Relations(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                version = request.version,
                owner = owner,
                notation = notation,
                linkType = linkType
            )
        )
        return notationMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update relation")
    fun updateRelation(
        @PathVariable id: UUID,
        @RequestBody request: RelationUpdateRequest
    ): RelationResponse {
        val relation = relationsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $id not found")
            }
        accessService.requireCanEditRelation(relation)

        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, relation.owner)
        val notation = request.notationId?.let {
            notationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $it not found")
            }
        }?.also { newNotation ->
            accessService.requireCanEditNotation(newNotation)
        } ?: relation.notation
        val linkType = request.linkTypeId?.let {
            linkTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $it not found")
            }
        }?.also { newLinkType ->
            typeUsageAuthorization.requireCanUseLinkTypeForNotation(newLinkType, notation)
        } ?: relation.linkType

        mdFileLinkValidator.validate(request.attrs)
        val updated = relationsRepository.save(
            relation.copy(
                name = request.name ?: relation.name,
                attrs = request.attrs ?: relation.attrs,
                version = request.version ?: relation.version,
                owner = owner,
                notation = notation,
                linkType = linkType
            )
        )
        return notationMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete relation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRelation(@PathVariable id: UUID) {
        val relation = relationsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $id not found")
            }
        accessService.requireCanEditRelation(relation)
        relationsRepository.deleteById(id)
    }
}
