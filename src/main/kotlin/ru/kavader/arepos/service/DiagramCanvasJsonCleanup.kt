package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/**
 * Удаляет из JSON attrs диаграммы экземпляры нод и рёбер, ссылающиеся на удалённые из модели сущности.
 * Согласовано с warchi: [sanitizeDiagramInstancesForModel], [ModelEditor.removeLinkFromModel], [markNodeDeleted].
 */
object DiagramCanvasJsonCleanup {

    const val DIAGRAM_NOTE_EDGE_MODEL_LINK_PREFIX: String = "__diagram-note-edge__:"

    fun cleanupDiagramAttrs(
        attrs: String?,
        objectMapper: ObjectMapper,
        deletedModelNodeIds: Set<UUID>,
        deletedModelLinkIds: Set<UUID>
    ): String? {
        if (attrs == null) return null
        if (deletedModelNodeIds.isEmpty() && deletedModelLinkIds.isEmpty()) return attrs

        val nodeIdStr = deletedModelNodeIds.map { it.toString() }.toSet()
        val linkIdStr = deletedModelLinkIds.map { it.toString() }.toSet()

        val root = try {
            objectMapper.readTree(attrs) as? ObjectNode ?: return attrs
        } catch (_: Exception) {
            return attrs
        }

        var changed = false
        if (nodeIdStr.isNotEmpty()) {
            changed = removeNodeInstancesFromRoot(root, objectMapper, nodeIdStr) || changed
        }
        if (linkIdStr.isNotEmpty()) {
            changed = removeEdgesForDeletedModelLinksFromRoot(root, objectMapper, linkIdStr) || changed
        }
        return if (changed) objectMapper.writeValueAsString(root) else attrs
    }

    private fun removeNodeInstancesFromRoot(root: ObjectNode, om: ObjectMapper, deletedModelNodeIds: Set<String>): Boolean {
        var c = false
        val inst = root.get("instances")
        if (inst is ObjectNode) {
            c = stripNodesAndDanglingEdges(inst, om, "nodes", "edges", deletedModelNodeIds) || c
        }
        c = stripNodesAndDanglingEdges(root, om, "nodes", "edges", deletedModelNodeIds) || c
        return c
    }

    private fun stripNodesAndDanglingEdges(
        container: ObjectNode,
        om: ObjectMapper,
        nodesKey: String,
        edgesKey: String,
        deletedModelNodeIds: Set<String>
    ): Boolean {
        val nodesJson = container.get(nodesKey) ?: return false
        if (!nodesJson.isArray) return false
        val nodesArray = nodesJson as ArrayNode

        val newNodes = om.createArrayNode()
        for (el in nodesArray) {
            if (!el.isObject) {
                newNodes.add(el)
                continue
            }
            val n = el as ObjectNode
            val mid = n.get("modelNodeId")?.asText()
            if (mid != null && mid in deletedModelNodeIds) {
                continue
            }
            newNodes.add(n)
        }
        val nodesChanged = newNodes.size() != nodesArray.size()
        if (nodesChanged) {
            container.replace(nodesKey, newNodes as JsonNode)
        }

        val keptInstanceIds = mutableSetOf<String>()
        for (el in newNodes) {
            if (el.isObject) {
                (el as ObjectNode).get("id")?.asText()?.let { keptInstanceIds.add(it) }
            }
        }

        var edgesChanged = false
        val edgesJson = container.get(edgesKey)
        if (edgesJson != null && edgesJson.isArray) {
            val edgesArray = edgesJson as ArrayNode
            val newEdges = om.createArrayNode()
            for (el in edgesArray) {
                if (!el.isObject) {
                    newEdges.add(el)
                    continue
                }
                val e = el as ObjectNode
                val src = e.get("sourceInstanceId")?.asText()
                val tgt = e.get("targetInstanceId")?.asText()
                if (src == null || tgt == null || src !in keptInstanceIds || tgt !in keptInstanceIds) {
                    continue
                }
                newEdges.add(e)
            }
            edgesChanged = newEdges.size() != edgesArray.size()
            if (edgesChanged) {
                container.replace(edgesKey, newEdges as JsonNode)
            }
        }
        return nodesChanged || edgesChanged
    }

    private fun removeEdgesForDeletedModelLinksFromRoot(root: ObjectNode, om: ObjectMapper, deletedLinkIds: Set<String>): Boolean {
        var c = false
        val inst = root.get("instances")
        if (inst is ObjectNode) {
            c = stripEdgesByModelLinkId(inst, om, "edges", deletedLinkIds) || c
        }
        c = stripEdgesByModelLinkId(root, om, "edges", deletedLinkIds) || c
        return c
    }

    private fun stripEdgesByModelLinkId(
        container: ObjectNode,
        om: ObjectMapper,
        edgesKey: String,
        deletedLinkIds: Set<String>
    ): Boolean {
        val edgesJson = container.get(edgesKey) ?: return false
        if (!edgesJson.isArray) return false
        val edgesArray = edgesJson as ArrayNode
        val newEdges = om.createArrayNode()
        for (el in edgesArray) {
            if (!el.isObject) {
                newEdges.add(el)
                continue
            }
            val e = el as ObjectNode
            if (preserveEdgeIgnoringModelLinkDelete(e)) {
                newEdges.add(e)
                continue
            }
            val mlid = e.get("modelLinkId")?.asText()
            if (mlid != null && mlid in deletedLinkIds) {
                continue
            }
            newEdges.add(e)
        }
        val changed = newEdges.size() != edgesArray.size()
        if (changed) {
            container.replace(edgesKey, newEdges as JsonNode)
        }
        return changed
    }

    private fun preserveEdgeIgnoringModelLinkDelete(edge: ObjectNode): Boolean {
        val mlid = edge.get("modelLinkId")?.asText()
        if (mlid != null && mlid.startsWith(DIAGRAM_NOTE_EDGE_MODEL_LINK_PREFIX)) {
            return true
        }
        val attrs = edge.get("attrs")
        if (attrs != null && attrs.isObject) {
            val diagramOnly = (attrs as ObjectNode).get("isDiagramOnly")
            if (diagramOnly != null && diagramOnly.isBoolean && diagramOnly.booleanValue()) {
                return true
            }
        }
        return false
    }
}
