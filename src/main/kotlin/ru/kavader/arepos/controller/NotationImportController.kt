package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.dto.import.NotationImportResponse
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/notations")
@Tag(name = "Notation Import", description = "Notation import and migration endpoints")
class NotationImportController(
    private val notationsRepository: NotationsRepository,
    private val usersRepository: UsersRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val relationRulesRepository: RelationRulesRepository,
    private val accessService: ResourceAccessService
) {

    @PostMapping("/import")
    @Operation(summary = "Import notation package")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun importNotation(@RequestBody @Valid request: NotationImportRequest): NotationImportResponse {
        val currentUserId = accessService.currentUserId()
        val owner = usersRepository.findById(currentUserId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $currentUserId not found")
            }

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
            val existing = nodeTypesRepository.findByNameIgnoreCase(importedNodeType.name.trim())
            if (existing != null) {
                nodeTypeIdMap[importedNodeType.id] = existing.id!!
            } else {
                val saved = nodeTypesRepository.save(
                    NodeTypes(
                        name = importedNodeType.name.trim(),
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
            val existing = linkTypesRepository.findByNameIgnoreCase(importedLinkType.name.trim())
            if (existing != null) {
                linkTypeIdMap[importedLinkType.id] = existing.id!!
            } else {
                val saved = linkTypesRepository.save(
                    LinkTypes(
                        name = importedLinkType.name.trim(),
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

            val allowedRelationIds = importedRule.allowedRelationIds
                .mapNotNull { relationIdMap[it] }
            if (allowedRelationIds.isEmpty()) continue

            for (relationUuid in allowedRelationIds) {
                val relation = relationsRepository.findById(relationUuid).orElse(null) ?: continue
                val fromComponent = componentsRepository.findById(fromComponentId).orElse(null) ?: continue
                val toComponent = componentsRepository.findById(toComponentId).orElse(null) ?: continue

                if (!relationRulesRepository.existsByRelationAndFromComponentAndToComponent(
                        relation, fromComponent, toComponent
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
