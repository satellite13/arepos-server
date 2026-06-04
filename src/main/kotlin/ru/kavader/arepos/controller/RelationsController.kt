package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.notation.NotationMapper
import ru.kavader.arepos.dto.notation.RelationRequest
import ru.kavader.arepos.dto.notation.RelationResponse
import ru.kavader.arepos.dto.notation.RelationUpdateRequest
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.*

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
        notationMapper.toResponse(findRelationForView(id))

    @PostMapping
    @Operation(summary = "Create relation")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRelation(@RequestBody request: RelationRequest): RelationResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val notation = findEditableNotation(request.notationId)
        val linkType = authorizedLinkType(request.linkTypeId, notation)
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
        val relation = findEditableRelation(id)
        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, relation.owner)
        val notation = resolveNotationForUpdate(request.notationId, relation.notation)
        val linkType = resolveLinkTypeForUpdate(request.linkTypeId, notation, relation.linkType)

        return NotationBoundEntityWriteSupport.persistUpdate(
            entity = relation,
            request = request,
            owner = owner,
            notation = notation,
            mdFileLinkValidator = mdFileLinkValidator,
            applyExtra = { this.linkType = linkType },
            save = relationsRepository::save,
            toResponse = notationMapper::toResponse
        )
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete relation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRelation(@PathVariable id: UUID) {
        findEditableRelation(id)
        relationsRepository.deleteById(id)
    }

    private fun findRelationOrThrow(id: UUID): Relations =
        relationsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $id not found")
        }

    private fun findRelationForView(id: UUID): Relations {
        val relation = findRelationOrThrow(id)
        accessService.requireCanViewRelation(relation)
        return relation
    }

    private fun findEditableRelation(id: UUID): Relations {
        val relation = findRelationOrThrow(id)
        accessService.requireCanEditRelation(relation)
        return relation
    }

    private fun findNotationOrThrow(id: UUID): Notations =
        notationsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
        }

    private fun findEditableNotation(notationId: UUID): Notations =
        findNotationOrThrow(notationId).also { accessService.requireCanEditNotation(it) }

    private fun resolveNotationForUpdate(requestNotationId: UUID?, current: Notations): Notations =
        requestNotationId?.let { findEditableNotation(it) } ?: current

    private fun authorizedLinkType(linkTypeId: UUID, notation: Notations): LinkTypes {
        val linkType = linkTypesRepository.findById(linkTypeId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $linkTypeId not found")
        }
        typeUsageAuthorization.requireCanUseLinkTypeForNotation(linkType, notation)
        return linkType
    }

    private fun resolveLinkTypeForUpdate(
        requestLinkTypeId: UUID?,
        notation: Notations,
        current: LinkTypes
    ): LinkTypes = requestLinkTypeId?.let { authorizedLinkType(it, notation) } ?: current
}
