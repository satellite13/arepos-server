package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
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
    private val nodeShapesRepository: NodeShapesRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val relationRulesBulkInserter: RelationRulesBulkInserter,
    private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
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
        val nodeTypesById = mutableMapOf<UUID, NodeTypes>()
        for (importedNodeType in request.nodeTypes) {
            val typeName = importedNodeType.name.trim()
            val existing = nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, typeName)
            if (existing != null) {
                val id = existing.id!!
                nodeTypeIdMap[importedNodeType.id] = id
                nodeTypesById[id] = existing
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
                val id = saved.id!!
                nodeTypeIdMap[importedNodeType.id] = id
                nodeTypesById[id] = saved
            }
        }

        val linkTypeIdMap = mutableMapOf<String, UUID>()
        val linkTypesById = mutableMapOf<UUID, LinkTypes>()
        for (importedLinkType in request.linkTypes) {
            val typeName = importedLinkType.name.trim()
            val existing = linkTypesRepository.findByOwnerAndNameIgnoreCase(owner, typeName)
            if (existing != null) {
                val id = existing.id!!
                linkTypeIdMap[importedLinkType.id] = id
                linkTypesById[id] = existing
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
                val id = saved.id!!
                linkTypeIdMap[importedLinkType.id] = id
                linkTypesById[id] = saved
            }
        }

        val shapeIdMap = mutableMapOf<String, UUID>()
        val takenShapeNames = nodeShapesRepository.findByOwner(owner)
            .map { it.name.lowercase() }
            .toMutableSet()
        for (importedShape in buildEffectiveShapes(request.shapes, request.components, objectMapper)) {
            val uniqueName = nextUniqueShapeName(importedShape.name, takenShapeNames)
            val savedShape = nodeShapesRepository.save(
                NodeShapes(
                    name = uniqueName,
                    owner = owner,
                    outline = importedShape.outline,
                    contentArea = importedShape.contentArea,
                    attrs = stripDocumentFileIdFromAttrs(importedShape.attrs, objectMapper),
                    createdAt = now,
                    updatedAt = now
                )
            )
            shapeIdMap[importedShape.id] = savedShape.id!!
        }

        val componentIdMap = mutableMapOf<String, UUID>()
        for (importedComponent in request.components) {
            val nodeTypeId = nodeTypeIdMap[importedComponent.nodeTypeId]
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown nodeTypeId '${importedComponent.nodeTypeId}' in component '${importedComponent.name}'"
                )
            val nodeType = nodeTypesById[nodeTypeId]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "NodeType $nodeTypeId not found")

            val saved = componentsRepository.save(
                Components(
                    name = importedComponent.name.trim(),
                    version = importedComponent.version?.trim()?.ifEmpty { null } ?: savedNotation.version,
                    attrs = remapCustomShapeIdInAttrs(importedComponent.attrs, shapeIdMap, objectMapper),
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
            val linkType = linkTypesById[linkTypeId]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "LinkType $linkTypeId not found")

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

        val ownerId = owner.id
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner id is required")
        val ruleRows = LinkedHashMap<String, RelationRulesBulkInserter.Row>()
        for (importedRule in request.relationRules) {
            val fromComponentId = componentIdMap[importedRule.fromComponentId] ?: continue
            val toComponentId = componentIdMap[importedRule.toComponentId] ?: continue
            for (sourceRelationId in importedRule.allowedRelationIds) {
                val relationId = relationIdMap[sourceRelationId] ?: continue
                val key = "$relationId|$fromComponentId|$toComponentId"
                ruleRows.putIfAbsent(
                    key,
                    RelationRulesBulkInserter.Row(
                        relationId = relationId,
                        fromComponentId = fromComponentId,
                        toComponentId = toComponentId,
                        ownerId = ownerId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
        if (ruleRows.isNotEmpty()) {
            // Flush JPA inserts so FK targets are visible to JDBC in this transaction.
            entityManager.flush()
            val startedAt = System.nanoTime()
            val inserted = relationRulesBulkInserter.insertIgnoreConflicts(ruleRows.values)
            logger.info(
                "NotationImport: bulk-inserted {}/{} relation_rules for '{}' in {} ms",
                inserted,
                ruleRows.size,
                notationName,
                (System.nanoTime() - startedAt) / 1_000_000
            )
        }

        return NotationImportResponse(
            notationId = savedNotation.id!!,
            nodeTypeIdMap = nodeTypeIdMap,
            linkTypeIdMap = linkTypeIdMap,
            componentIdMap = componentIdMap,
            relationIdMap = relationIdMap,
            shapeIdMap = shapeIdMap
        )
    }
}
