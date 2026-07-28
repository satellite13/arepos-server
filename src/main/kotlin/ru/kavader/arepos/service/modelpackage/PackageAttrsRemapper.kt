package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Remaps package-local id references in model/node/link/diagram attrs after import.
 */
@Component
class PackageAttrsRemapper(
    private val objectMapper: ObjectMapper
) {
    private val mdFileLinkRewriter = MdFileLinkRewriter(objectMapper)

    fun remapModelOrEntityAttrs(
        attrs: String?,
        fileIdMap: Map<UUID, UUID>,
        notationIdMap: Map<UUID, UUID> = emptyMap(),
        componentIdMap: Map<String, UUID> = emptyMap(),
        relationIdMap: Map<String, UUID> = emptyMap()
    ): String? {
        if (attrs.isNullOrBlank()) return attrs
        var current = mdFileLinkRewriter.rewriteAttrsJson(attrs, fileIdMap) ?: attrs
        current = remapBindingMaps(current, notationIdMap, componentIdMap, relationIdMap)
        return current
    }

    fun remapDiagramExtras(
        attrs: String?,
        fileIdMap: Map<UUID, UUID>,
        componentIdMap: Map<String, UUID>,
        relationIdMap: Map<String, UUID>
    ): String? {
        if (attrs.isNullOrBlank()) return attrs
        var current = mdFileLinkRewriter.rewriteAttrsJson(attrs, fileIdMap) ?: attrs
        if (componentIdMap.isEmpty() && relationIdMap.isEmpty()) return current

        val root = try {
            objectMapper.readTree(current)
        } catch (_: Exception) {
            return current
        }
        if (!root.isObject) return current
        val rootObj = root as ObjectNode

        remapInstanceComponentFields(rootObj.get("instances"), componentIdMap, relationIdMap)
        remapInstanceComponentFields(rootObj, componentIdMap, relationIdMap)

        return objectMapper.writeValueAsString(rootObj)
    }

    private fun remapBindingMaps(
        attrs: String,
        notationIdMap: Map<UUID, UUID>,
        componentIdMap: Map<String, UUID>,
        relationIdMap: Map<String, UUID>
    ): String {
        if (notationIdMap.isEmpty() && componentIdMap.isEmpty() && relationIdMap.isEmpty()) {
            return attrs
        }
        val root = try {
            objectMapper.readTree(attrs)
        } catch (_: Exception) {
            return attrs
        }
        if (!root.isObject) return attrs
        val rootObj = root as ObjectNode

        remapKeyedBindingObject(
            parent = rootObj,
            fieldName = "notationComponents",
            notationIdMap = notationIdMap,
            idField = "componentId",
            idMap = componentIdMap
        )
        remapKeyedBindingObject(
            parent = rootObj,
            fieldName = "notationRelations",
            notationIdMap = notationIdMap,
            idField = "relationId",
            idMap = relationIdMap
        )
        remapScopedPropertyKeys(rootObj, "componentProperties", notationIdMap, componentIdMap)
        remapScopedPropertyKeys(rootObj, "relationProperties", notationIdMap, relationIdMap)

        return objectMapper.writeValueAsString(rootObj)
    }

    private fun remapKeyedBindingObject(
        parent: ObjectNode,
        fieldName: String,
        notationIdMap: Map<UUID, UUID>,
        idField: String,
        idMap: Map<String, UUID>
    ) {
        val node = parent.get(fieldName) as? ObjectNode ?: return
        val remapped = objectMapper.createObjectNode()
        val fields = node.fields()
        while (fields.hasNext()) {
            val entry = fields.next()
            val key = entry.key
            val value = entry.value
            val newKey = remapUuidKey(key, notationIdMap) ?: key
            if (value is ObjectNode) {
                val copy = value.deepCopy()
                val oldId = copy.get(idField)?.asText()
                if (oldId != null) {
                    idMap[oldId]?.let { copy.put(idField, it.toString()) }
                }
                remapped.set<ObjectNode>(newKey, copy)
            } else {
                remapped.set<JsonNode>(newKey, value)
            }
        }
        parent.set<ObjectNode>(fieldName, remapped)
    }

    private fun remapScopedPropertyKeys(
        parent: ObjectNode,
        fieldName: String,
        notationIdMap: Map<UUID, UUID>,
        entityIdMap: Map<String, UUID>
    ) {
        val scoped = parent.get(fieldName) as? ObjectNode ?: return
        val remappedScoped = objectMapper.createObjectNode()
        val notationFields = scoped.fields()
        while (notationFields.hasNext()) {
            val notationEntry = notationFields.next()
            val notationKey = remapUuidKey(notationEntry.key, notationIdMap) ?: notationEntry.key
            val byEntity = notationEntry.value
            if (byEntity is ObjectNode) {
                val remappedEntities = objectMapper.createObjectNode()
                val entityFields = byEntity.fields()
                while (entityFields.hasNext()) {
                    val entityEntry = entityFields.next()
                    val newEntityKey = entityIdMap[entityEntry.key]?.toString() ?: entityEntry.key
                    remappedEntities.set<JsonNode>(newEntityKey, entityEntry.value)
                }
                remappedScoped.set<ObjectNode>(notationKey, remappedEntities)
            } else {
                remappedScoped.set<JsonNode>(notationKey, byEntity)
            }
        }
        parent.set<ObjectNode>(fieldName, remappedScoped)
    }

    private fun remapInstanceComponentFields(
        parent: JsonNode?,
        componentIdMap: Map<String, UUID>,
        relationIdMap: Map<String, UUID>
    ) {
        if (parent == null || !parent.isObject) return
        val parentObj = parent as ObjectNode
        val nodes = parentObj.get("nodes")
        if (nodes != null && nodes.isArray) {
            for (element in nodes) {
                if (element !is ObjectNode) continue
                val attrs = element.get("attrs") as? ObjectNode
                val target = attrs ?: element
                val componentId = target.get("notationComponentId")?.asText()
                    ?: target.get("componentId")?.asText()
                if (componentId != null) {
                    componentIdMap[componentId]?.let {
                        if (target.has("notationComponentId")) {
                            target.put("notationComponentId", it.toString())
                        } else if (target.has("componentId")) {
                            target.put("componentId", it.toString())
                        }
                    }
                }
            }
        }
        val edges = parentObj.get("edges")
        if (edges != null && edges.isArray) {
            for (element in edges) {
                if (element !is ObjectNode) continue
                val attrs = element.get("attrs") as? ObjectNode
                val target = attrs ?: element
                val relationId = target.get("relationId")?.asText()
                if (relationId != null) {
                    relationIdMap[relationId]?.let { target.put("relationId", it.toString()) }
                }
            }
        }
    }

    private fun remapUuidKey(key: String, notationIdMap: Map<UUID, UUID>): String? {
        return try {
            val uuid = UUID.fromString(key)
            notationIdMap[uuid]?.toString()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
