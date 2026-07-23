package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import ru.kavader.arepos.dto.import.ImportedComponent
import ru.kavader.arepos.dto.import.ImportedNodeShape
import java.util.UUID

internal fun buildEffectiveShapes(
    packageShapes: List<ImportedNodeShape>,
    components: List<ImportedComponent>,
    objectMapper: ObjectMapper
): List<ImportedNodeShape> {
    val byId = LinkedHashMap<String, ImportedNodeShape>()
    for (shape in packageShapes) {
        byId[shape.id] = shape
    }
    for (component in components) {
        val attrs = component.attrs ?: continue
        try {
            val root = objectMapper.readTree(attrs)
            if (!root.isObject) continue
            val diagramStyle = root.get("diagramStyle") ?: continue
            if (!diagramStyle.isObject) continue
            val customShapeId = diagramStyle.get("customShapeId")?.asText()?.trim().orEmpty()
            if (customShapeId.isEmpty() || byId.containsKey(customShapeId)) continue
            val customOutline = diagramStyle.get("customOutline") ?: continue
            if (!customOutline.isArray || customOutline.isEmpty) continue
            byId[customShapeId] = ImportedNodeShape(
                id = customShapeId,
                name = "Imported shape",
                outline = objectMapper.writeValueAsString(customOutline)
            )
        } catch (_: Exception) {
            continue
        }
    }
    return byId.values.toList()
}

internal fun nextUniqueShapeName(baseName: String, takenNamesLowercase: MutableSet<String>): String {
    val base = baseName.trim().ifEmpty { "Imported shape" }
    if (base.lowercase() !in takenNamesLowercase) {
        takenNamesLowercase.add(base.lowercase())
        return base
    }
    var counter = 2
    while (true) {
        val candidate = "$base ($counter)"
        if (candidate.lowercase() !in takenNamesLowercase) {
            takenNamesLowercase.add(candidate.lowercase())
            return candidate
        }
        counter++
    }
}

internal fun stripDocumentFileIdFromAttrs(attrs: String?, objectMapper: ObjectMapper): String? {
    if (attrs.isNullOrBlank()) return attrs
    return try {
        val root = objectMapper.readTree(attrs)
        if (!root.isObject || !root.has("documentFileId")) return attrs
        (root as ObjectNode).remove("documentFileId")
        objectMapper.writeValueAsString(root)
    } catch (_: Exception) {
        attrs
    }
}

internal fun remapCustomShapeIdInAttrs(
    attrs: String?,
    shapeIdMap: Map<String, UUID>,
    objectMapper: ObjectMapper
): String? {
    if (attrs.isNullOrBlank() || shapeIdMap.isEmpty()) return attrs
    return try {
        val root = objectMapper.readTree(attrs)
        if (!root.isObject) return attrs
        val diagramStyle = root.get("diagramStyle") ?: return attrs
        if (!diagramStyle.isObject) return attrs
        val oldId = diagramStyle.get("customShapeId")?.asText()?.trim().orEmpty()
        if (oldId.isEmpty()) return attrs
        val newId = shapeIdMap[oldId] ?: return attrs
        (diagramStyle as ObjectNode).put("customShapeId", newId.toString())
        objectMapper.writeValueAsString(root)
    } catch (_: Exception) {
        attrs
    }
}
