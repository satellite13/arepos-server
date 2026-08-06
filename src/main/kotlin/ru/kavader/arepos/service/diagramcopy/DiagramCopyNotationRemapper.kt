package ru.kavader.arepos.service.diagramcopy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.model.DiagramCopyWarning
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Relations
import java.util.UUID

data class RemapAttrsResult(
    val attrs: String?,
    val warnings: List<DiagramCopyWarning>
)

@Component
class DiagramCopyNotationRemapper(
    private val objectMapper: ObjectMapper
) {

    fun remapDiagramAttrs(
        attrs: String?,
        componentIdMap: Map<UUID, UUID>,
        relationIdMap: Map<UUID, UUID>
    ): RemapAttrsResult {
        val root = parseObject(attrs) ?: return RemapAttrsResult(attrs, emptyList())
        val warnings = WarningCollector()

        if (root.remove("documentFileId") != null) {
            warnings.add("DOCUMENT_NOT_COPIED", "Document attachment was not copied")
        }

        remapDiagramInstances(root, componentIdMap, relationIdMap, warnings)
        root.get("instances")?.let { remapDiagramInstances(it as? ObjectNode, componentIdMap, relationIdMap, warnings) }

        return RemapAttrsResult(objectMapper.writeValueAsString(root), warnings.warnings)
    }

    fun remapNodeAttrs(
        attrs: String?,
        sourceNotationId: UUID,
        targetNotationId: UUID,
        componentIdMap: Map<UUID, UUID>
    ): RemapAttrsResult = remapEntityAttrs(
        attrs = attrs,
        sourceNotationId = sourceNotationId,
        targetNotationId = targetNotationId,
        bindingField = "notationComponents",
        bindingIdField = "componentId",
        propertiesField = "componentProperties",
        idMap = componentIdMap,
        warningCode = "NOTATION_COMPONENT_NOT_MAPPED",
        warningLabel = "component"
    )

    fun remapLinkAttrs(
        attrs: String?,
        sourceNotationId: UUID,
        targetNotationId: UUID,
        relationIdMap: Map<UUID, UUID>
    ): RemapAttrsResult = remapEntityAttrs(
        attrs = attrs,
        sourceNotationId = sourceNotationId,
        targetNotationId = targetNotationId,
        bindingField = "notationRelations",
        bindingIdField = "relationId",
        propertiesField = "relationProperties",
        idMap = relationIdMap,
        warningCode = "NOTATION_RELATION_NOT_MAPPED",
        warningLabel = "relation"
    )

    fun buildComponentIdMap(
        source: List<Components>,
        target: List<Components>
    ): Pair<Map<UUID, UUID>, List<String>> = buildIdMap(
        source = source,
        target = target,
        name = { it.name },
        typeId = { it.nodeType.id },
        id = { it.id }
    )

    fun buildRelationIdMap(
        source: List<Relations>,
        target: List<Relations>
    ): Pair<Map<UUID, UUID>, List<String>> = buildIdMap(
        source = source,
        target = target,
        name = { it.name },
        typeId = { it.linkType.id },
        id = { it.id }
    )

    private fun remapDiagramInstances(
        parent: ObjectNode?,
        componentIdMap: Map<UUID, UUID>,
        relationIdMap: Map<UUID, UUID>,
        warnings: WarningCollector
    ) {
        parent ?: return
        (parent.get("nodes") as? ArrayNode)?.forEach { instance ->
            val instanceObject = instance as? ObjectNode ?: return@forEach
            val binding = (instanceObject.get("attrs") as? ObjectNode) ?: instanceObject
            remapBindingField(
                binding,
                listOf("notationComponentId", "componentId"),
                componentIdMap,
                "NOTATION_COMPONENT_NOT_MAPPED",
                "component",
                warnings
            )
        }
        (parent.get("edges") as? ArrayNode)?.forEach { instance ->
            val instanceObject = instance as? ObjectNode ?: return@forEach
            val binding = (instanceObject.get("attrs") as? ObjectNode) ?: instanceObject
            remapBindingField(
                binding,
                listOf("notationRelationId", "relationId"),
                relationIdMap,
                "NOTATION_RELATION_NOT_MAPPED",
                "relation",
                warnings
            )
        }
    }

    private fun remapEntityAttrs(
        attrs: String?,
        sourceNotationId: UUID,
        targetNotationId: UUID,
        bindingField: String,
        bindingIdField: String,
        propertiesField: String,
        idMap: Map<UUID, UUID>,
        warningCode: String,
        warningLabel: String
    ): RemapAttrsResult {
        val root = parseObject(attrs) ?: return RemapAttrsResult(attrs, emptyList())
        val warnings = WarningCollector()
        val sourceKey = sourceNotationId.toString()
        val targetKey = targetNotationId.toString()

        (root.get(bindingField) as? ObjectNode)?.let { bindings ->
            val binding = bindings.remove(sourceKey) as? ObjectNode
            if (binding != null) {
                val mappedId = binding.get(bindingIdField)?.asText()?.toUuidOrNull()?.let(idMap::get)
                if (mappedId == null) {
                    val originalId = binding.get(bindingIdField)?.asText()
                    warnings.add(warningCode, "Notation $warningLabel ${originalId ?: "binding"} was not mapped")
                } else {
                    val targetBinding = (bindings.get(targetKey) as? ObjectNode) ?: objectMapper.createObjectNode()
                    targetBinding.put(bindingIdField, mappedId.toString())
                    bindings.set<ObjectNode>(targetKey, targetBinding)
                }
            }
        }

        remapScopedProperties(
            root = root,
            fieldName = propertiesField,
            sourceKey = sourceKey,
            targetKey = targetKey,
            idMap = idMap,
            warningCode = warningCode,
            warningLabel = warningLabel,
            warnings = warnings
        )

        return RemapAttrsResult(objectMapper.writeValueAsString(root), warnings.warnings)
    }

    private fun remapScopedProperties(
        root: ObjectNode,
        fieldName: String,
        sourceKey: String,
        targetKey: String,
        idMap: Map<UUID, UUID>,
        warningCode: String,
        warningLabel: String,
        warnings: WarningCollector
    ) {
        val scoped = root.get(fieldName) as? ObjectNode ?: return
        val sourceProperties = scoped.remove(sourceKey) as? ObjectNode ?: return
        val targetProperties = (scoped.get(targetKey) as? ObjectNode) ?: objectMapper.createObjectNode()

        sourceProperties.properties().forEach { (oldId, value) ->
            val mappedId = oldId.toUuidOrNull()?.let(idMap::get)
            if (mappedId == null) {
                warnings.add(warningCode, "Notation $warningLabel $oldId was not mapped")
            } else {
                targetProperties.set<JsonNode>(mappedId.toString(), value)
            }
        }
        if (targetProperties.size() > 0) {
            scoped.set<ObjectNode>(targetKey, targetProperties)
        }
    }

    private fun remapBindingField(
        obj: ObjectNode,
        fieldNames: List<String>,
        idMap: Map<UUID, UUID>,
        warningCode: String,
        warningLabel: String,
        warnings: WarningCollector
    ) {
        fieldNames.forEach { fieldName ->
            val oldId = obj.get(fieldName)?.asText()?.toUuidOrNull() ?: return@forEach
            val mappedId = idMap[oldId]
            if (mappedId == null) {
                obj.remove(fieldName)
                warnings.add(warningCode, "Notation $warningLabel $oldId was not mapped")
            } else {
                obj.put(fieldName, mappedId.toString())
            }
        }
    }

    private fun parseObject(attrs: String?): ObjectNode? {
        if (attrs.isNullOrBlank()) return null
        return try {
            objectMapper.readTree(attrs) as? ObjectNode
        } catch (_: Exception) {
            null
        }
    }

    private fun <T> buildIdMap(
        source: List<T>,
        target: List<T>,
        name: (T) -> String,
        typeId: (T) -> UUID?,
        id: (T) -> UUID?
    ): Pair<Map<UUID, UUID>, List<String>> {
        val targetByName = target.groupBy(name)
        val targetByNameAndType = target.groupBy { "${name(it)}\u0000${typeId(it)}" }
        val mapped = mutableMapOf<UUID, UUID>()
        val unmapped = sortedSetOf<String>()

        source.forEach { sourceItem ->
            val nameMatches = targetByName[name(sourceItem)].orEmpty()
            val match = nameMatches.singleOrNull()
                ?: targetByNameAndType["${name(sourceItem)}\u0000${typeId(sourceItem)}"].orEmpty().singleOrNull()
            val sourceId = id(sourceItem)
            val targetId = match?.let(id)
            if (sourceId != null && targetId != null) {
                mapped[sourceId] = targetId
            } else {
                unmapped += name(sourceItem)
            }
        }
        return mapped to unmapped.toList()
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private class WarningCollector {
        private val seen = mutableSetOf<Pair<String, String>>()
        val warnings = mutableListOf<DiagramCopyWarning>()

        fun add(code: String, message: String) {
            if (seen.add(code to message)) {
                warnings += DiagramCopyWarning(code, message)
            }
        }
    }
}
