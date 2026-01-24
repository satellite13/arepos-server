package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/relation-rules")
class RelationRulesController(
    private val relationRulesRepository: RelationRulesRepository,
    private val usersRepository: UsersRepository,
    private val relationsRepository: RelationsRepository,
    private val componentsRepository: ComponentsRepository
) {

    @GetMapping
    fun listRelationRules(
        pageable: Pageable,
        @RequestParam(required = false) relationId: UUID?,
        @RequestParam(required = false) ownerId: UUID?
    ): Page<RelationRuleResponse> {
        val relationRules = when {
            relationId != null && ownerId != null -> {
                val relation = relationsRepository.findById(relationId).orElse(null)
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (relation != null && owner != null) {
                    relationRulesRepository.findByRelationAndOwner(relation, owner, pageable)
                } else {
                    relationRulesRepository.findAll(pageable)
                }
            }
            relationId != null -> {
                val relation = relationsRepository.findById(relationId).orElse(null)
                if (relation != null) {
                    relationRulesRepository.findByRelation(relation, pageable)
                } else {
                    relationRulesRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    relationRulesRepository.findByOwner(owner, pageable)
                } else {
                    relationRulesRepository.findAll(pageable)
                }
            }
            else -> {
                relationRulesRepository.findAll(pageable)
            }
        }
        return relationRules.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getRelationRule(@PathVariable id: UUID): RelationRuleResponse =
        relationRulesRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "RelationRule $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRelationRule(@RequestBody request: RelationRuleRequest): RelationRuleResponse {
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val relation = relationsRepository.findById(request.relationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation ${request.relationId} not found")
            }
        val fromComponent = componentsRepository.findById(request.fromComponentId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "FromComponent ${request.fromComponentId} not found")
            }
        val toComponent = componentsRepository.findById(request.toComponentId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "ToComponent ${request.toComponentId} not found")
            }
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
        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: relationRule.owner
        val relation = request.relationId?.let {
            relationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $it not found")
            }
        } ?: relationRule.relation
        val fromComponent = request.fromComponentId?.let {
            componentsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "FromComponent $it not found")
            }
        } ?: relationRule.fromComponent
        val toComponent = request.toComponentId?.let {
            componentsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "ToComponent $it not found")
            }
        } ?: relationRule.toComponent

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
        if (!relationRulesRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "RelationRule $id not found")
        }
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
}

data class RelationRuleRequest(
    val relationId: UUID,
    val fromComponentId: UUID,
    val toComponentId: UUID,
    val ownerId: UUID,
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

