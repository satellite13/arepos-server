package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.system.RelationRulesSyncRequest
import ru.kavader.arepos.dto.system.RelationRulesSyncResponse
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/notations/{notationId}/relation-rules")
@Tag(name = "Relation Rules Sync", description = "Bulk relation rules synchronization endpoints")
class RelationRulesSyncController(
    private val relationRulesRepository: RelationRulesRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val notationsRepository: NotationsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {

    @PutMapping("/sync")
    @Operation(summary = "Synchronize relation rules for notation")
    @Transactional
    fun syncRelationRules(
        @PathVariable notationId: UUID,
        @RequestBody request: RelationRulesSyncRequest
    ): RelationRulesSyncResponse {
        val notation = notationsRepository.findById(notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $notationId not found")
            }
        accessService.requireCanEditNotation(notation)

        val currentUserId = accessService.currentUserId()
        val owner = usersRepository.findById(currentUserId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $currentUserId not found")
            }

        val existingRules = relationRulesRepository.findByFilters(
            relationId = null,
            ownerId = null,
            notationId = notationId,
            pageable = Pageable.unpaged()
        ).content

        data class RuleKey(val fromComponentId: UUID, val toComponentId: UUID, val relationId: UUID)

        val desiredKeys = mutableSetOf<RuleKey>()
        for (item in request.rules) {
            for (relationId in item.allowedRelationIds) {
                desiredKeys.add(RuleKey(item.fromComponentId, item.toComponentId, relationId))
            }
        }

        val existingKeyMap = existingRules.associateBy { rule ->
            RuleKey(rule.fromComponent.id!!, rule.toComponent.id!!, rule.relation.id!!)
        }

        val toDelete = existingRules.filter { rule ->
            RuleKey(rule.fromComponent.id!!, rule.toComponent.id!!, rule.relation.id!!) !in desiredKeys
        }

        val existingKeySet = existingKeyMap.keys
        val toCreate = desiredKeys.filter { it !in existingKeySet }

        relationRulesRepository.deleteAll(toDelete)

        val componentCache = mutableMapOf<UUID, ru.kavader.arepos.model.Components>()
        val relationCache = mutableMapOf<UUID, ru.kavader.arepos.model.Relations>()

        val now = Instant.now()
        for (key in toCreate) {
            val fromComponent = componentCache.getOrPut(key.fromComponentId) {
                componentsRepository.findById(key.fromComponentId)
                    .orElseThrow {
                        ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Component ${key.fromComponentId} not found"
                        )
                    }
            }
            val toComponent = componentCache.getOrPut(key.toComponentId) {
                componentsRepository.findById(key.toComponentId)
                    .orElseThrow {
                        ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Component ${key.toComponentId} not found"
                        )
                    }
            }
            val relation = relationCache.getOrPut(key.relationId) {
                relationsRepository.findById(key.relationId)
                    .orElseThrow {
                        ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Relation ${key.relationId} not found"
                        )
                    }
            }

            relationRulesRepository.save(
                RelationRules(
                    createdAt = now,
                    updatedAt = now,
                    owner = owner,
                    relation = relation,
                    fromComponent = fromComponent,
                    toComponent = toComponent
                )
            )
        }

        val total = existingRules.size - toDelete.size + toCreate.size
        return RelationRulesSyncResponse(
            created = toCreate.size,
            deleted = toDelete.size,
            total = total
        )
    }
}

