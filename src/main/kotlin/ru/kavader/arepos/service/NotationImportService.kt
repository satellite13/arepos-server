package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.dto.import.NotationImportResponse
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.*
import java.time.Instant
import java.util.UUID

/**
 * Imports a new notation owned by the authenticated caller.
 *
 * Node and link types are reused by name within the importer's ownership when they already
 * exist. Import does not target or modify an existing notation, so it intentionally does not
 * call `requireCanEditNotation`; authentication of the caller is the only authorization
 * prerequisite.
 */
@Service
class NotationImportService(
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val relationRulesRepository: RelationRulesRepository
) {
    @Transactional
    fun import(request: NotationImportRequest, owner: Users): NotationImportResponse {
        val now = Instant.now()
        val notationName = request.notation.name.trim().ifEmpty {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Notation name is required")
        }
        val notationVersion = request.notation.version.trim().ifEmpty { "1.0.0" }

        if (notationsRepository.existsByNameAndVersion(notationName, notationVersion)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Notation with name '$notationName' and version '$notationVersion' already exists"
            )
        }

        val savedNotation = notationsRepository.save(
            Notations(
                name = notationName,
                version = notationVersion,
                owner = owner,
                attrs = request.notation.attrs,
                createdAt = now,
                updatedAt = now,
                deleted = false
            )
        )

        val nodeTypeIdMap = mutableMapOf<String, UUID>()
        for (importedNodeType in request.nodeTypes) {
            val typeName = importedNodeType.name.trim()
            val existing = nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, typeName)
            if (existing != null) {
                nodeTypeIdMap[importedNodeType.id] = existing.id!!
            } else {
                val saved = nodeTypesRepository.save(
                    NodeTypes(
                        name = typeName,
                        attrs = importedNodeType.attrs,
                        owner = owner,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                nodeTypeIdMap[importedNodeType.id] = saved.id!!
            }
        }

        val linkTypeIdMap = mutableMapOf<String, UUID>()
        for (importedLinkType in request.linkTypes) {
            val typeName = importedLinkType.name.trim()
            val existing = linkTypesRepository.findByOwnerAndNameIgnoreCase(owner, typeName)
            if (existing != null) {
                linkTypeIdMap[importedLinkType.id] = existing.id!!
            } else {
                val saved = linkTypesRepository.save(
                    LinkTypes(
                        name = typeName,
                        attrs = importedLinkType.attrs,
                        owner = owner,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                linkTypeIdMap[importedLinkType.id] = saved.id!!
            }
        }

        val componentIdMap = mutableMapOf<String, UUID>()
        for (importedComponent in request.components) {
            val nodeTypeId = nodeTypeIdMap[importedComponent.nodeTypeId]
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown nodeTypeId '${importedComponent.nodeTypeId}' in component '${importedComponent.name}'"
                )
            val nodeType = nodeTypesRepository.findById(nodeTypeId)
                .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "NodeType $nodeTypeId not found") }

            val saved = componentsRepository.save(
                Components(
                    name = importedComponent.name.trim(),
                    version = importedComponent.version?.trim()?.ifEmpty { null } ?: savedNotation.version,
                    attrs = importedComponent.attrs,
                    notation = savedNotation,
                    owner = owner,
                    nodeType = nodeType,
                    createdAt = now,
                    updatedAt = now
                )
            )
            componentIdMap[importedComponent.id] = saved.id!!
        }

        val relationIdMap = mutableMapOf<String, UUID>()
        for (importedRelation in request.relations) {
            val linkTypeId = linkTypeIdMap[importedRelation.linkTypeId]
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown linkTypeId '${importedRelation.linkTypeId}' in relation '${importedRelation.name}'"
                )
            val linkType = linkTypesRepository.findById(linkTypeId)
                .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "LinkType $linkTypeId not found") }

            val saved = relationsRepository.save(
                Relations(
                    name = importedRelation.name.trim(),
                    version = importedRelation.version?.trim()?.ifEmpty { null } ?: savedNotation.version,
                    attrs = importedRelation.attrs,
                    notation = savedNotation,
                    owner = owner,
                    linkType = linkType,
                    createdAt = now,
                    updatedAt = now
                )
            )
            relationIdMap[importedRelation.id] = saved.id!!
        }

        for (importedRule in request.relationRules) {
            val fromComponentId = componentIdMap[importedRule.fromComponentId] ?: continue
            val toComponentId = componentIdMap[importedRule.toComponentId] ?: continue
            val allowedRelationIds = importedRule.allowedRelationIds.mapNotNull { relationIdMap[it] }
            if (allowedRelationIds.isEmpty()) continue

            for (relationUuid in allowedRelationIds) {
                val relation = relationsRepository.findById(relationUuid).orElse(null) ?: continue
                val fromComponent = componentsRepository.findById(fromComponentId).orElse(null) ?: continue
                val toComponent = componentsRepository.findById(toComponentId).orElse(null) ?: continue
                if (!relationRulesRepository.existsByRelationAndFromComponentAndToComponent(
                        relation,
                        fromComponent,
                        toComponent
                    )
                ) {
                    relationRulesRepository.save(
                        RelationRules(
                            relation = relation,
                            fromComponent = fromComponent,
                            toComponent = toComponent,
                            owner = owner,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
        }

        return NotationImportResponse(
            notationId = savedNotation.id!!,
            nodeTypeIdMap = nodeTypeIdMap,
            linkTypeIdMap = linkTypeIdMap,
            componentIdMap = componentIdMap,
            relationIdMap = relationIdMap
        )
    }
}
