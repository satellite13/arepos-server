package ru.kavader.arepos.service

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.system.RelationRulesSyncRequest
import ru.kavader.arepos.dto.system.RelationRulesSyncResponse
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class RelationRulesSyncService(
    private val relationRulesRepository: RelationRulesRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val notationsRepository: NotationsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {
    private data class RuleKey(
        val fromComponentId: UUID,
        val toComponentId: UUID,
        val relationId: UUID
    )

    @Transactional
    fun sync(notationId: UUID, request: RelationRulesSyncRequest): RelationRulesSyncResponse {
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
        val desiredKeys = request.rules.flatMapTo(mutableSetOf()) { item ->
            item.allowedRelationIds.map { RuleKey(item.fromComponentId, item.toComponentId, it) }
        }
        val existingKeys = existingRules.associateBy {
            RuleKey(it.fromComponent.id!!, it.toComponent.id!!, it.relation.id!!)
        }
        val toDelete = existingRules.filter {
            RuleKey(it.fromComponent.id!!, it.toComponent.id!!, it.relation.id!!) !in desiredKeys
        }
        val toCreate = desiredKeys.filter { it !in existingKeys.keys }
        relationRulesRepository.deleteAll(toDelete)

        val componentCache = mutableMapOf<UUID, Components>()
        val relationCache = mutableMapOf<UUID, Relations>()
        val now = Instant.now()
        for (key in toCreate) {
            val fromComponent = componentCache.getOrPut(key.fromComponentId) {
                findComponent(key.fromComponentId)
            }
            val toComponent = componentCache.getOrPut(key.toComponentId) {
                findComponent(key.toComponentId)
            }
            val relation = relationCache.getOrPut(key.relationId) {
                relationsRepository.findById(key.relationId).orElseThrow {
                    ResponseStatusException(HttpStatus.BAD_REQUEST, "Relation ${key.relationId} not found")
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
        return RelationRulesSyncResponse(
            created = toCreate.size,
            deleted = toDelete.size,
            total = existingRules.size - toDelete.size + toCreate.size
        )
    }

    private fun findComponent(id: UUID): Components =
        componentsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Component $id not found")
        }
}
