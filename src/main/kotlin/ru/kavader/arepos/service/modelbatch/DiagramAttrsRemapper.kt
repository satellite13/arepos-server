package ru.kavader.arepos.service.modelbatch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DiagramAttrsRemapper(
    private val objectMapper: ObjectMapper
) {
    fun remap(attrs: String?, nodeIdMap: Map<String, UUID>, linkIdMap: Map<String, UUID>): String? {
        if (attrs == null) return null
        if (nodeIdMap.isEmpty() && linkIdMap.isEmpty()) return attrs

        val root = try {
            objectMapper.readTree(attrs)
        } catch (_: Exception) {
            return attrs
        }
        if (!root.isObject) return attrs

        var changed = false
        val rootObj = root as ObjectNode

        val instances = rootObj.get("instances")
        if (instances != null && instances.isObject) {
            val instObj = instances as ObjectNode
            val inNodes = instObj.get("nodes")
            if (inNodes != null && inNodes.isArray) {
                for (element in inNodes) {
                    if (element is ObjectNode) {
                        changed = remapField(element, "modelNodeId", nodeIdMap) || changed
                    }
                }
            }
            val inEdges = instObj.get("edges")
            if (inEdges != null && inEdges.isArray) {
                for (element in inEdges) {
                    if (element is ObjectNode) {
                        changed = remapField(element, "modelLinkId", linkIdMap) || changed
                        changed = remapField(element, "sourceModelNodeId", nodeIdMap) || changed
                        changed = remapField(element, "targetModelNodeId", nodeIdMap) || changed
                    }
                }
            }
        }

        val nodesArray = rootObj.get("nodes")
        if (nodesArray != null && nodesArray.isArray) {
            for (element in nodesArray) {
                if (element is ObjectNode) {
                    changed = remapField(element, "modelNodeId", nodeIdMap) || changed
                }
            }
        }

        val edgesArray = rootObj.get("edges")
        if (edgesArray != null && edgesArray.isArray) {
            for (element in edgesArray) {
                if (element is ObjectNode) {
                    changed = remapField(element, "modelLinkId", linkIdMap) || changed
                    changed = remapField(element, "sourceModelNodeId", nodeIdMap) || changed
                    changed = remapField(element, "targetModelNodeId", nodeIdMap) || changed
                }
            }
        }

        return if (changed) objectMapper.writeValueAsString(rootObj) else attrs
    }

    private fun remapField(obj: ObjectNode, field: String, idMap: Map<String, UUID>): Boolean {
        val value = obj.get(field)?.asText() ?: return false
        val mapped = idMap[value] ?: return false
        obj.put(field, mapped.toString())
        return true
    }
}
