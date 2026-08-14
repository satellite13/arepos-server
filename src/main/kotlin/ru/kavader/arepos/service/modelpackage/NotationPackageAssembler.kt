package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.dto.import.ImportedComponent
import ru.kavader.arepos.dto.import.ImportedLibraryIcon
import ru.kavader.arepos.dto.import.ImportedLinkType
import ru.kavader.arepos.dto.import.ImportedNodeShape
import ru.kavader.arepos.dto.import.ImportedNodeType
import ru.kavader.arepos.dto.import.ImportedRelation
import ru.kavader.arepos.dto.import.ImportedRelationRule
import ru.kavader.arepos.dto.import.NotationImportMeta
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.service.LibraryIconNameCollector
import ru.kavader.arepos.service.LibraryIconService
import ru.kavader.arepos.service.buildEffectiveShapes
import ru.kavader.arepos.service.stripDocumentFileIdFromAttrs
import java.time.Instant
import java.util.UUID

@Service
class NotationPackageAssembler(
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val relationRulesRepository: RelationRulesRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val nodeShapesRepository: NodeShapesRepository,
    private val objectMapper: ObjectMapper,
    private val libraryIconNameCollector: LibraryIconNameCollector,
    private val libraryIconService: LibraryIconService
) {
    /** Flat payload for model package `notations/<id>.json` and NotationImportService. */
    @Transactional(readOnly = true)
    fun toImportRequest(notation: Notations): NotationImportRequest {
        val snapshot = loadSnapshot(notation)
        return NotationImportRequest(
            notation = NotationImportMeta(
                name = notation.name,
                version = notation.version,
                attrs = notation.attrs
            ),
            nodeTypes = snapshot.nodeTypes.map { type ->
                ImportedNodeType(
                    id = type.id!!.toString(),
                    name = type.name,
                    attrs = type.attrs
                )
            },
            linkTypes = snapshot.linkTypes.map { type ->
                ImportedLinkType(
                    id = type.id!!.toString(),
                    name = type.name,
                    attrs = type.attrs
                )
            },
            components = snapshot.components.map { component ->
                ImportedComponent(
                    id = component.id!!.toString(),
                    name = component.name,
                    nodeTypeId = component.nodeType.id!!.toString(),
                    version = component.version,
                    attrs = component.attrs
                )
            },
            relations = snapshot.relations.map { relation ->
                ImportedRelation(
                    id = relation.id!!.toString(),
                    name = relation.name,
                    linkTypeId = relation.linkType.id!!.toString(),
                    version = relation.version,
                    attrs = relation.attrs
                )
            },
            relationRules = snapshot.relationRules,
            shapes = snapshot.shapes,
            icons = collectLibraryIcons(snapshot, notation.attrs).map { icon ->
                ImportedLibraryIcon(name = icon.getValue("name"), svg = icon.getValue("svg"))
            }
        )
    }

    /** Client download shape matching warchi `warchi-notation-export` v2. */
    @Transactional(readOnly = true)
    fun toClientExportDocument(notation: Notations): Map<String, Any?> {
        val snapshot = loadSnapshot(notation)
        val notationId = notation.id!!.toString()
        val ownerId = notation.owner.id!!.toString()

        return linkedMapOf(
            "format" to "warchi-notation-export",
            "version" to 2,
            "exportedAt" to Instant.now().toString(),
            "notation" to linkedMapOf(
                "id" to notationId,
                "name" to notation.name,
                "version" to notation.version
            ),
            "state" to linkedMapOf(
                "notationId" to notationId,
                "ownerId" to ownerId,
                "nodeTypes" to snapshot.nodeTypes.map { type ->
                    linkedMapOf(
                        "id" to type.id!!.toString(),
                        "name" to type.name,
                        "ownerId" to type.owner.id!!.toString(),
                        "createdAt" to type.createdAt?.toString(),
                        "updatedAt" to type.updatedAt?.toString(),
                        "parsedAttrs" to parseAttrsObject(type.attrs)
                    )
                },
                "linkTypes" to snapshot.linkTypes.map { type ->
                    linkedMapOf(
                        "id" to type.id!!.toString(),
                        "name" to type.name,
                        "ownerId" to type.owner.id!!.toString(),
                        "createdAt" to type.createdAt?.toString(),
                        "updatedAt" to type.updatedAt?.toString(),
                        "parsedAttrs" to parseAttrsObject(type.attrs)
                    )
                },
                "components" to snapshot.components.map { component ->
                    linkedMapOf(
                        "id" to component.id!!.toString(),
                        "name" to component.name,
                        "version" to component.version,
                        "notationId" to notationId,
                        "ownerId" to component.owner.id!!.toString(),
                        "nodeTypeId" to component.nodeType.id!!.toString(),
                        "createdAt" to component.createdAt?.toString(),
                        "updatedAt" to component.updatedAt?.toString(),
                        "parsedAttrs" to parseAttrsObject(component.attrs)
                    )
                },
                "relations" to snapshot.relations.map { relation ->
                    linkedMapOf(
                        "id" to relation.id!!.toString(),
                        "name" to relation.name,
                        "version" to relation.version,
                        "notationId" to notationId,
                        "ownerId" to relation.owner.id!!.toString(),
                        "linkTypeId" to relation.linkType.id!!.toString(),
                        "createdAt" to relation.createdAt?.toString(),
                        "updatedAt" to relation.updatedAt?.toString(),
                        "parsedAttrs" to parseAttrsObject(relation.attrs)
                    )
                },
                "relationRules" to snapshot.relationRules.mapIndexed { index, rule ->
                    linkedMapOf(
                        "id" to "rule-$index",
                        "fromComponentId" to rule.fromComponentId,
                        "toComponentId" to rule.toComponentId,
                        "allowedRelationIds" to rule.allowedRelationIds
                    )
                },
                "diagramLayer" to extractDiagramLayer(notation.attrs)
            ),
            "shapes" to snapshot.shapes.map { shape ->
                linkedMapOf(
                    "id" to shape.id,
                    "name" to shape.name,
                    "outline" to (shape.outline ?: "[]"),
                    "contentArea" to shape.contentArea,
                    "attrs" to shape.attrs
                )
            },
            "icons" to collectLibraryIcons(snapshot, notation.attrs)
        )
    }

    private fun collectLibraryIcons(snapshot: NotationSnapshot, notationAttrs: String?): List<Map<String, String>> {
        val names = linkedSetOf<String>()
        names += libraryIconNameCollector.collectFromJson(notationAttrs)
        snapshot.nodeTypes.forEach { names += libraryIconNameCollector.collectFromJson(it.attrs) }
        snapshot.linkTypes.forEach { names += libraryIconNameCollector.collectFromJson(it.attrs) }
        snapshot.components.forEach { names += libraryIconNameCollector.collectFromJson(it.attrs) }
        snapshot.relations.forEach { names += libraryIconNameCollector.collectFromJson(it.attrs) }
        snapshot.shapes.forEach { names += libraryIconNameCollector.collectFromJson(it.attrs) }
        if (names.isEmpty()) return emptyList()
        return libraryIconService.findByNames(names)
            .sortedBy { it.name }
            .map { icon -> linkedMapOf("name" to icon.name, "svg" to icon.svg) }
    }

    private data class NotationSnapshot(
        val components: List<Components>,
        val relations: List<Relations>,
        val nodeTypes: List<NodeTypes>,
        val linkTypes: List<LinkTypes>,
        val relationRules: List<ImportedRelationRule>,
        val shapes: List<ImportedNodeShape>
    )

    private fun loadSnapshot(notation: Notations): NotationSnapshot {
        val managed = notationsRepository.findById(requireNotNull(notation.id)).orElse(notation)
        // Touch owner for lazy load inside the transaction.
        managed.owner.id

        val components = componentsRepository.findByNotation(managed, Pageable.unpaged()).content
        val relations = relationsRepository.findByNotation(managed, Pageable.unpaged()).content

        val nodeTypeIds = components.mapNotNull { it.nodeType.id }.toSet()
        val linkTypeIds = relations.mapNotNull { it.linkType.id }.toSet()
        val nodeTypes = if (nodeTypeIds.isEmpty()) {
            emptyList()
        } else {
            nodeTypesRepository.findAllById(nodeTypeIds)
        }
        val linkTypes = if (linkTypeIds.isEmpty()) {
            emptyList()
        } else {
            linkTypesRepository.findAllById(linkTypeIds)
        }

        val componentIds = components.mapNotNull { it.id }.toSet()
        val relationIds = relations.mapNotNull { it.id }.toSet()
        val relationById = relations.associateBy { it.id!! }

        val aggregatedRules = linkedMapOf<Pair<String, String>, MutableList<String>>()
        for (relation in relations) {
            val rules = relationRulesRepository.findByRelation(relation, Pageable.unpaged()).content
            for (rule in rules) {
                val fromId = rule.fromComponent.id ?: continue
                val toId = rule.toComponent.id ?: continue
                if (fromId !in componentIds || toId !in componentIds) continue
                val relationId = rule.relation.id ?: continue
                if (relationId !in relationIds) continue
                // Ensure relation entity is one of the loaded ones (same notation).
                if (relationById[relationId] == null) continue
                val key = fromId.toString() to toId.toString()
                aggregatedRules.getOrPut(key) { mutableListOf() }.add(relationId.toString())
            }
        }
        val relationRules = aggregatedRules.map { (key, allowed) ->
            ImportedRelationRule(
                fromComponentId = key.first,
                toComponentId = key.second,
                allowedRelationIds = allowed.distinct()
            )
        }.filter { it.allowedRelationIds.isNotEmpty() }

        val importedComponents = components.map { component ->
            ImportedComponent(
                id = component.id!!.toString(),
                name = component.name,
                nodeTypeId = component.nodeType.id!!.toString(),
                version = component.version,
                attrs = component.attrs
            )
        }
        val shapeIds = collectCustomShapeIds(importedComponents)
        val loadedShapes = if (shapeIds.isEmpty()) {
            emptyList()
        } else {
            nodeShapesRepository.findAllById(shapeIds).map { shape ->
                toImportedShape(shape)
            }
        }
        val shapes = buildEffectiveShapes(loadedShapes, importedComponents, objectMapper)
            .map { shape ->
                shape.copy(attrs = stripDocumentFileIdFromAttrs(shape.attrs, objectMapper))
            }

        return NotationSnapshot(
            components = components,
            relations = relations,
            nodeTypes = nodeTypes,
            linkTypes = linkTypes,
            relationRules = relationRules,
            shapes = shapes
        )
    }

    private fun collectCustomShapeIds(components: List<ImportedComponent>): Set<UUID> {
        val ids = linkedSetOf<UUID>()
        for (component in components) {
            val attrs = component.attrs ?: continue
            try {
                val root = objectMapper.readTree(attrs)
                val customShapeId = root.path("diagramStyle").path("customShapeId").asText(null)
                    ?.trim()
                    .orEmpty()
                if (customShapeId.isEmpty()) continue
                runCatching { UUID.fromString(customShapeId) }.getOrNull()?.let { ids.add(it) }
            } catch (_: Exception) {
                continue
            }
        }
        return ids
    }

    private fun toImportedShape(shape: NodeShapes): ImportedNodeShape =
        ImportedNodeShape(
            id = shape.id!!.toString(),
            name = shape.name,
            outline = shape.outline,
            contentArea = shape.contentArea,
            attrs = stripDocumentFileIdFromAttrs(shape.attrs, objectMapper)
        )

    private fun parseAttrsObject(attrs: String?): Any {
        if (attrs.isNullOrBlank()) return emptyMap<String, Any?>()
        return try {
            val node = objectMapper.readTree(attrs)
            if (node.isObject) objectMapper.convertValue(node, Map::class.java) else emptyMap<String, Any?>()
        } catch (_: Exception) {
            emptyMap<String, Any?>()
        }
    }

    private fun extractDiagramLayer(attrs: String?): Map<String, Any?> {
        val empty = linkedMapOf<String, Any?>(
            "version" to 1,
            "nodes" to emptyList<Any>(),
            "edges" to emptyList<Any>()
        )
        if (attrs.isNullOrBlank()) return empty
        return try {
            val root = objectMapper.readTree(attrs)
            val layer: JsonNode = when {
                root.path("diagramLayer").isObject -> root.path("diagramLayer")
                root.path("editorDiagramLayer").isObject -> root.path("editorDiagramLayer")
                else -> return empty
            }
            @Suppress("UNCHECKED_CAST")
            objectMapper.convertValue(layer, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            empty
        }
    }
}
