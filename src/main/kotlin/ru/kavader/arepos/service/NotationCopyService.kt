package ru.kavader.arepos.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import java.time.Instant

@Service
class NotationCopyService(
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val relationRulesRepository: RelationRulesRepository
) {
    @Transactional
    fun copyNotation(source: Notations, owner: Users, name: String, version: String, attrs: String?): Notations {
        val now = Instant.now()
        val newNotation = notationsRepository.save(
            Notations(
                name = name,
                version = version,
                owner = owner,
                attrs = attrs ?: source.attrs,
                createdAt = now,
                updatedAt = now,
                source = source,
                deleted = false
            )
        )

        val sourceComponents = componentsRepository.findByNotation(source, Pageable.unpaged())
        val copiedComponentsBySourceId = mutableMapOf<java.util.UUID, Components>()
        for (srcComponent in sourceComponents) {
            val saved = componentsRepository.save(
                Components(
                    name = srcComponent.name,
                    attrs = srcComponent.attrs,
                    createdAt = now,
                    updatedAt = now,
                    version = newNotation.version,
                    notation = newNotation,
                    owner = owner,
                    nodeType = srcComponent.nodeType
                )
            )
            val sourceComponentId = requireNotNull(srcComponent.id)
            copiedComponentsBySourceId[sourceComponentId] = saved
        }

        val sourceRelations = relationsRepository.findByNotation(source, Pageable.unpaged())
        val copiedRelationsBySourceId = mutableMapOf<java.util.UUID, Relations>()
        for (srcRelation in sourceRelations) {
            val saved = relationsRepository.save(
                Relations(
                    attrs = srcRelation.attrs,
                    createdAt = now,
                    updatedAt = now,
                    version = newNotation.version,
                    owner = owner,
                    notation = newNotation,
                    name = srcRelation.name,
                    linkType = srcRelation.linkType
                )
            )
            val sourceRelationId = requireNotNull(srcRelation.id)
            copiedRelationsBySourceId[sourceRelationId] = saved
        }

        for (srcRelation in sourceRelations) {
            val sourceRules = relationRulesRepository.findByRelation(srcRelation, Pageable.unpaged())
            val sourceRelationId = requireNotNull(srcRelation.id)
            val newRelation = copiedRelationsBySourceId[sourceRelationId]
                ?: throw IllegalStateException("Copied relation not found")

            for (srcRule in sourceRules) {
                val sourceFromId = requireNotNull(srcRule.fromComponent.id)
                val sourceToId = requireNotNull(srcRule.toComponent.id)
                val newFrom = copiedComponentsBySourceId[sourceFromId] ?: continue
                val newTo = copiedComponentsBySourceId[sourceToId] ?: continue

                relationRulesRepository.save(
                    RelationRules(
                        createdAt = now,
                        updatedAt = now,
                        owner = owner,
                        attrs = srcRule.attrs,
                        relation = newRelation,
                        fromComponent = newFrom,
                        toComponent = newTo
                    )
                )
            }
        }

        return newNotation
    }
}
