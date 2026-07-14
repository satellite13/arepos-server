package ru.kavader.arepos.service.modelbatch

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class DiagramAttrsRemapper(
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun remap(attrs: String?, nodeIdMap: Map<String, UUID>, linkIdMap: Map<String, UUID>): String? {
        if (attrs == null) return null
        if (nodeIdMap.isEmpty() && linkIdMap.isEmpty()) return attrs

        val root = try {
            objectMapper.readTree(attrs)
        } catch (_: JsonProcessingException) {
            log.debug("Invalid diagram attrs JSON; preserving original attrs (attrsLength={})", attrs.length)
            return attrs
        }
        if (!root.isObject) return attrs

        var changed = false
        val rootObj = root as ObjectNode

        val instances = rootObj.get("instances")
        if (instances != null && instances.isObject) {
            val instObj = instances as ObjectNode
            changed = "nodes".remapNodesArray(instObj, nodeIdMap) || false
            changed = "edges".remapEdgesArray(instObj, nodeIdMap, linkIdMap) || changed
        }

        changed = "nodes".remapNodesArray(rootObj, nodeIdMap) || changed
        changed = "edges".remapEdgesArray(rootObj, nodeIdMap, linkIdMap) || changed

        return if (changed) objectMapper.writeValueAsString(rootObj) else attrs
    }

    private fun String.remapNodesArray(parent: ObjectNode, nodeIdMap: Map<String, UUID>): Boolean {
        val array = parent.get(this) as? ArrayNode ?: return false
        var changed = false
        for (element in array) {
            if (element is ObjectNode) {
                changed = remapField(element, "modelNodeId", nodeIdMap) || changed
            }
        }
        return changed
    }

    private fun String.remapEdgesArray(
        parent: ObjectNode,
        nodeIdMap: Map<String, UUID>,
        linkIdMap: Map<String, UUID>
    ): Boolean {
        val array = parent.get(this) as? ArrayNode ?: return false
        var changed = false
        for (element in array) {
            if (element is ObjectNode) {
                changed = remapField(element, "modelLinkId", linkIdMap) || changed
                changed = remapField(element, "sourceModelNodeId", nodeIdMap) || changed
                changed = remapField(element, "targetModelNodeId", nodeIdMap) || changed
            }
        }
        return changed
    }

    private fun remapField(obj: ObjectNode, field: String, idMap: Map<String, UUID>): Boolean {
        val value = obj.get(field)?.asText() ?: return false
        val mapped = idMap[value] ?: return false
        obj.put(field, mapped.toString())
        return true
    }
}
