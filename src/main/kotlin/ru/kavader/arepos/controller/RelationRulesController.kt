package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.notation.RelationRuleRequest
import ru.kavader.arepos.dto.notation.RelationRuleResponse
import ru.kavader.arepos.dto.notation.RelationRuleUpdateRequest
import ru.kavader.arepos.mapper.NotationMapper
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/relation-rules")
@Tag(name = "Relation Rules", description = "Relation rule management endpoints")
class RelationRulesController(
    private val relationRulesRepository: RelationRulesRepository,
    private val relationsRepository: RelationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val notationMapper: NotationMapper
) {

    @GetMapping
    @Operation(summary = "List relation rules")
    fun listRelationRules(
        pageable: Pageable,
        @RequestParam(required = false) relationId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false, defaultValue = "true") includeAttrs: Boolean
    ): Page<RelationRuleResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = CurrentUser.getId() ?: return Page.empty(pageable)
            return if (includeAttrs) {
                relationRulesRepository.findProjectedByFiltersForUser(
                    relationId = relationId,
                    ownerId = ownerId,
                    notationId = notationId,
                    currentUserId = currentUserId,
                    diagramEditorModelId = modelId,
                    pageable = pageable
                ).map { notationMapper.toResponse(it, includeAttrs = true) }
            } else {
                relationRulesRepository.findProjectedLightByFiltersForUser(
                    relationId = relationId,
                    ownerId = ownerId,
                    notationId = notationId,
                    currentUserId = currentUserId,
                    diagramEditorModelId = modelId,
                    pageable = pageable
                ).map { notationMapper.toResponse(it) }
            }
        }

        return if (includeAttrs) {
            val relationRules = relationRulesRepository.findProjectedByFilters(
                relationId = relationId,
                ownerId = ownerId,
                notationId = notationId,
                pageable = pageable
            )
            relationRules.map { notationMapper.toResponse(it, includeAttrs = true) }
        } else {
            val relationRules = relationRulesRepository.findProjectedLightByFilters(
                relationId = relationId,
                ownerId = ownerId,
                notationId = notationId,
                pageable = pageable
            )
            relationRules.map { notationMapper.toResponse(it) }
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get relation rule by id")
    fun getRelationRule(@PathVariable id: UUID): RelationRuleResponse =
        relationRulesRepository.findById(id)
            .map {
                accessService.requireCanViewRelationRule(it)
                notationMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "RelationRule $id not found")
            }

    @PostMapping
    @Operation(summary = "Create relation rule")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRelationRule(@RequestBody @Valid request: RelationRuleRequest): RelationRuleResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val relation = relationsRepository.findById(request.relationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation ${request.relationId} not found")
            }
        accessService.requireCanEditRelation(relation)
        val fromComponent = componentsRepository.findById(request.fromComponentId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "FromComponent ${request.fromComponentId} not found")
            }
        accessService.requireCanEditComponent(fromComponent)
        val toComponent = componentsRepository.findById(request.toComponentId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "ToComponent ${request.toComponentId} not found")
            }
        accessService.requireCanEditComponent(toComponent)
        if (relationRulesRepository.existsByRelationAndFromComponentAndToComponent(
                relation,
                fromComponent,
                toComponent
            )
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Relation rule with the same relation/from/to already exists"
            )
        }
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = relationRulesRepository.save(
            RelationRules(
                createdAt = now,
                updatedAt = now,
                owner = owner,
                attrs = request.attrs,
                relation = relation,
                fromComponent = fromComponent,
                toComponent = toComponent
            )
        )
        return notationMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update relation rule")
    fun updateRelationRule(
        @PathVariable id: UUID,
        @RequestBody @Valid request: RelationRuleUpdateRequest
    ): RelationRuleResponse {
        val relationRule = relationRulesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "RelationRule $id not found")
            }
        accessService.requireCanEditRelationRule(relationRule)

        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, relationRule.owner)
        val relation = request.relationId?.let {
            relationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $it not found")
            }
        }?.also { newRelation ->
            accessService.requireCanEditRelation(newRelation)
        } ?: relationRule.relation
        val fromComponent = request.fromComponentId?.let {
            componentsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "FromComponent $it not found")
            }
        }?.also { newFrom ->
            accessService.requireCanEditComponent(newFrom)
        } ?: relationRule.fromComponent
        val toComponent = request.toComponentId?.let {
            componentsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "ToComponent $it not found")
            }
        }?.also { newTo ->
            accessService.requireCanEditComponent(newTo)
        } ?: relationRule.toComponent
        if (
            relationRulesRepository.existsByRelationAndFromComponentAndToComponentAndIdNot(
                relation,
                fromComponent,
                toComponent,
                id
            )
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Relation rule with the same relation/from/to already exists"
            )
        }

        mdFileLinkValidator.validate(request.attrs)
        relationRule.owner = owner
        relationRule.attrs = request.attrs ?: relationRule.attrs
        relationRule.relation = relation
        relationRule.fromComponent = fromComponent
        relationRule.toComponent = toComponent
        val updated = relationRulesRepository.save(relationRule)
        return notationMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete relation rule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRelationRule(@PathVariable id: UUID) {
        val relationRule = relationRulesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "RelationRule $id not found")
            }
        accessService.requireCanEditRelationRule(relationRule)
        relationRulesRepository.deleteById(id)
    }

}
