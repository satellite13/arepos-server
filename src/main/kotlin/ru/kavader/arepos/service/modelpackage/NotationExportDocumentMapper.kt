package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.import.ImportedComponent
import ru.kavader.arepos.dto.import.ImportedLibraryIcon
import ru.kavader.arepos.dto.import.ImportedLinkType
import ru.kavader.arepos.dto.import.ImportedNodeShape
import ru.kavader.arepos.dto.import.ImportedNodeType
import ru.kavader.arepos.dto.import.ImportedRelation
import ru.kavader.arepos.dto.import.ImportedRelationRule
import ru.kavader.arepos.dto.import.NotationImportMeta
import ru.kavader.arepos.dto.import.NotationImportRequest

@Component
class NotationExportDocumentMapper(
    private val objectMapper: ObjectMapper
) {
    fun toImportRequest(root: JsonNode): NotationImportRequest {
        if (!isExportDocument(root)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a warchi-notation-export document")
        }
        val version = root.path("version").asInt(-1)
        if (version != 2) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported notation export version: $version")
        }
        val notationNode = root.path("notation")
        val name = notationNode.path("name").asText("").trim()
        if (name.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Notation name is required")
        }
        val notationVersion = notationNode.path("version").asText("").trim().ifEmpty { "1.0.0" }
        val state = root.path("state")

        val attrsObject = objectMapper.createObjectNode()
        val diagramLayer = state.path("diagramLayer")
        if (diagramLayer.isObject) {
            attrsObject.set<JsonNode>("diagramLayer", diagramLayer.deepCopy())
        }

        return NotationImportRequest(
            notation = NotationImportMeta(
                name = name,
                version = notationVersion,
                attrs = if (attrsObject.size() == 0) null else objectMapper.writeValueAsString(attrsObject)
            ),
            nodeTypes = mapTypes(state.path("nodeTypes")) { id, n, attrs ->
                ImportedNodeType(id = id, name = n, attrs = attrs)
            },
            linkTypes = mapTypes(state.path("linkTypes")) { id, n, attrs ->
                ImportedLinkType(id = id, name = n, attrs = attrs)
            },
            components = mapEntities(state.path("components")) { id, n, version, typeId, attrs ->
                ImportedComponent(
                    id = id,
                    name = n,
                    nodeTypeId = typeId ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Component '$n' missing nodeTypeId"
                    ),
                    version = version,
                    attrs = attrs
                )
            },
            relations = mapEntities(state.path("relations")) { id, n, version, typeId, attrs ->
                ImportedRelation(
                    id = id,
                    name = n,
                    linkTypeId = typeId ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Relation '$n' missing linkTypeId"
                    ),
                    version = version,
                    attrs = attrs
                )
            },
            relationRules = mapRelationRules(state.path("relationRules")),
            shapes = mapShapes(root.path("shapes")),
            icons = mapIcons(root.path("icons"))
        )
    }

    companion object {
        fun isExportDocument(root: JsonNode): Boolean =
            root.path("format").asText(null) == "warchi-notation-export"
    }

    private fun <T> mapTypes(
        array: JsonNode,
        factory: (id: String, name: String, attrs: String?) -> T
    ): List<T> {
        if (!array.isArray) return emptyList()
        return array.mapNotNull { node ->
            val id = node.path("id").asText("").trim()
            val name = node.path("name").asText("").trim()
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            factory(id, name, attrsFromNode(node))
        }
    }

    private fun <T> mapEntities(
        array: JsonNode,
        factory: (id: String, name: String, version: String?, typeId: String?, attrs: String?) -> T
    ): List<T> {
        if (!array.isArray) return emptyList()
        return array.mapNotNull { node ->
            val id = node.path("id").asText("").trim()
            val name = node.path("name").asText("").trim()
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            val version = node.path("version").asText(null)?.trim()?.ifEmpty { null }
            val typeId = when {
                node.hasNonNull("nodeTypeId") -> node.path("nodeTypeId").asText(null)
                node.hasNonNull("linkTypeId") -> node.path("linkTypeId").asText(null)
                else -> null
            }?.trim()?.ifEmpty { null }
            factory(id, name, version, typeId, attrsFromNode(node))
        }
    }

    private fun mapRelationRules(array: JsonNode): List<ImportedRelationRule> {
        if (!array.isArray) return emptyList()
        return array.mapNotNull { node ->
            val from = node.path("fromComponentId").asText("").trim()
            val to = node.path("toComponentId").asText("").trim()
            if (from.isEmpty() || to.isEmpty()) return@mapNotNull null
            val allowed = node.path("allowedRelationIds")
                .takeIf { it.isArray }
                ?.mapNotNull { it.asText(null)?.trim()?.ifEmpty { null } }
                .orEmpty()
            ImportedRelationRule(fromComponentId = from, toComponentId = to, allowedRelationIds = allowed)
        }
    }

    private fun mapShapes(array: JsonNode): List<ImportedNodeShape> {
        if (!array.isArray) return emptyList()
        return array.mapNotNull { node ->
            val id = node.path("id").asText("").trim()
            val name = node.path("name").asText("").trim()
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            val attrsNode = node.get("attrs")
            val attrs = when {
                attrsNode == null || attrsNode.isNull -> null
                attrsNode.isTextual -> attrsNode.asText()
                else -> objectMapper.writeValueAsString(attrsNode)
            }
            ImportedNodeShape(
                id = id,
                name = name,
                outline = node.path("outline").asText(null),
                contentArea = node.path("contentArea").asText(null),
                attrs = attrs
            )
        }
    }

    /** Prefer `parsedAttrs` object (export v2); fall back to string `attrs`. */
    private fun attrsFromNode(node: JsonNode): String? {
        val parsed = node.get("parsedAttrs")
        if (parsed != null && parsed.isObject) {
            return objectMapper.writeValueAsString(parsed)
        }
        val attrs = node.get("attrs") ?: return null
        if (attrs.isNull) return null
        if (attrs.isTextual) return attrs.asText()
        if (attrs.isObject) return objectMapper.writeValueAsString(attrs)
        return null
    }

    private fun mapIcons(array: JsonNode): List<ImportedLibraryIcon> {
        if (!array.isArray) return emptyList()
        return array.mapNotNull { node ->
            val name = node.path("name").asText("").trim()
            val svg = node.path("svg").asText("").trim()
            if (name.isEmpty() || svg.isEmpty()) null
            else ImportedLibraryIcon(name = name, svg = svg)
        }
    }
}
