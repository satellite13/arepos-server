package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
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
    private val diagramsRepository: DiagramsRepository,
    private val accessService: ResourceAccessService,
    private val mdFileLinkValidator: MdFileLinkValidator
) {

    @GetMapping
    fun listRelationRules(
        pageable: Pageable,
        @RequestParam(required = false) relationId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) notationId: UUID?
    ): Page<RelationRuleResponse> {
        if (!CurrentUser.isAdmin()) {
            val accessibleNotationIds = diagramsRepository.findAll(Pageable.unpaged()).content
                .asSequence()
                .filter { accessService.canViewDiagram(it) }
                .mapNotNull { it.notation.id }
                .toSet()
            val filtered = relationRulesRepository
                .findByFilters(relationId, ownerId, notationId, Pageable.unpaged())
                .content
                .asSequence()
                .filter {
                    accessService.canViewRelationRule(it) || accessibleNotationIds.contains(it.relation.notation.id)
                }
                .toList()
            return filtered.toPage(pageable).map { it.toResponse() }
        }

        val relationRules = relationRulesRepository.findByFilters(
            relationId = relationId,
            ownerId = ownerId,
            notationId = notationId,
            pageable = pageable
        )
        return relationRules.map { it.toResponse() }
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
        val currentUserId = accessService.currentUserId()
        val resolvedOwnerId = if (CurrentUser.isAdmin()) {
            request.ownerId ?: currentUserId
        } else {
            currentUserId
        }
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
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

        val owner = if (CurrentUser.isAdmin()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: relationRule.owner
        } else {
            relationRule.owner
        }
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

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isEditorOrAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
    }

    private fun getCurrentUser() = CurrentUser.getId()?.let {
        usersRepository.findById(it).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $it not found")
        }
    } ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

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
}

data class RelationRuleRequest(
    val relationId: UUID,
    val fromComponentId: UUID,
    val toComponentId: UUID,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class RelationRuleUpdateRequest(
    val relationId: UUID? = null,
    val fromComponentId: UUID? = null,
    val toComponentId: UUID? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class RelationRuleResponse(
    val id: UUID,
    val relationId: UUID,
    val fromComponentId: UUID,
    val toComponentId: UUID,
    val ownerId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
