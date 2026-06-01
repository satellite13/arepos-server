package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.notation.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.repository.RelationRuleListLightProjection
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.RelationRuleListProjection
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/relation-rules")
class RelationRulesController(
    private val relationRulesRepository: RelationRulesRepository,
    private val usersRepository: UsersRepository,
    private val relationsRepository: RelationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator
) {

    @GetMapping
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
                ).map { it.toResponse(includeAttrs = true) }
            } else {
                relationRulesRepository.findProjectedLightByFiltersForUser(
                    relationId = relationId,
                    ownerId = ownerId,
                    notationId = notationId,
                    currentUserId = currentUserId,
                    diagramEditorModelId = modelId,
                    pageable = pageable
                ).map { it.toResponse() }
            }
        }

        return if (includeAttrs) {
            val relationRules = relationRulesRepository.findProjectedByFilters(
                relationId = relationId,
                ownerId = ownerId,
                notationId = notationId,
                pageable = pageable
            )
            relationRules.map { it.toResponse(includeAttrs = true) }
        } else {
            val relationRules = relationRulesRepository.findProjectedLightByFilters(
                relationId = relationId,
                ownerId = ownerId,
                notationId = notationId,
                pageable = pageable
            )
            relationRules.map { it.toResponse() }
        }
    }

    @GetMapping("/{id}")
    fun getRelationRule(@PathVariable id: UUID): RelationRuleResponse =
        relationRulesRepository.findById(id)
            .map {
                accessService.requireCanViewRelationRule(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "RelationRule $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRelationRule(@RequestBody request: RelationRuleRequest): RelationRuleResponse {
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
        if (relationRulesRepository.existsByRelationAndFromComponentAndToComponent(relation, fromComponent, toComponent)) {
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
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateRelationRule(
        @PathVariable id: UUID,
        @RequestBody request: RelationRuleUpdateRequest
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
        val updated = relationRulesRepository.save(
            relationRule.copy(
                owner = owner,
                attrs = request.attrs ?: relationRule.attrs,
                relation = relation,
                fromComponent = fromComponent,
                toComponent = toComponent
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRelationRule(@PathVariable id: UUID) {
        val relationRule = relationRulesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "RelationRule $id not found")
            }
        accessService.requireCanEditRelationRule(relationRule)
        relationRulesRepository.deleteById(id)
    }

    private fun RelationRules.toResponse() = RelationRuleResponse(
        id = requireNotNull(id),
        relationId = relation.id!!,
        fromComponentId = fromComponent.id!!,
        toComponentId = toComponent.id!!,
        ownerId = owner.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun RelationRuleListProjection.toResponse(includeAttrs: Boolean) = RelationRuleResponse(
        id = id,
        relationId = relationId,
        fromComponentId = fromComponentId,
        toComponentId = toComponentId,
        ownerId = ownerId,
        attrs = if (includeAttrs) attrs else null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun RelationRuleListLightProjection.toResponse() = RelationRuleResponse(
        id = id,
        relationId = relationId,
        fromComponentId = fromComponentId,
        toComponentId = toComponentId,
        ownerId = ownerId,
        attrs = null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
