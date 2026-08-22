package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import ru.kavader.arepos.dto.search.ModelSearchHit
import ru.kavader.arepos.repository.NodeAncestorProjection
import ru.kavader.arepos.repository.NodesRepository
import java.util.UUID

@Service
class ModelSearchPathEnricher(
    private val nodesRepository: NodesRepository,
    private val objectMapper: ObjectMapper
) {
    fun enrich(modelId: UUID, modelAttrs: String?, hits: List<ModelSearchHit>): List<ModelSearchHit> {
        if (hits.isEmpty()) {
            return hits
        }
        val configuredRootId = readConfiguredRootId(modelAttrs)
        val cache = mutableMapOf<UUID, List<String>?>()

        fun nodePathNames(nodeId: UUID): List<String>? =
            cache.getOrPut(nodeId) { resolveNodePathNames(modelId, nodeId, configuredRootId) }

        return hits.map { hit ->
            when (hit.kind) {
                "node" -> hit.copy(pathNames = nodePathNames(hit.id))
                "diagram" -> enrichDiagramPath(hit, ::nodePathNames)
                else -> hit
            }
        }
    }

    private fun enrichDiagramPath(
        hit: ModelSearchHit,
        nodePathNames: (UUID) -> List<String>?
    ): ModelSearchHit {
        val diagramName = hit.name?.trim().orEmpty()
        val parentId = hit.parentId
        if (parentId == null) {
            return hit.copy(pathNames = diagramName.takeIf { it.isNotEmpty() }?.let(::listOf))
        }
        val parentPath = nodePathNames(parentId) ?: return hit.copy(pathNames = null)
        if (diagramName.isEmpty()) {
            return hit.copy(pathNames = parentPath)
        }
        return hit.copy(pathNames = parentPath + diagramName)
    }

    private fun resolveNodePathNames(
        modelId: UUID,
        nodeId: UUID,
        configuredRootId: UUID?
    ): List<String>? {
        val path = nodesRepository.findAncestorPath(
            modelId = modelId,
            nodeId = nodeId,
            configuredRootId = configuredRootId,
            maxDepthPlusOne = MAX_DEPTH + 1
        )
        if (path.isEmpty() || !isValidPath(path, modelId, configuredRootId)) {
            return null
        }
        val names = path.asSequence()
            .sortedByDescending(NodeAncestorProjection::getDepth)
            .filterNot { it.getHiddenTreeRoot() || it.getId() == configuredRootId }
            .map { it.getName() }
            .toList()
        return names.takeIf { it.isNotEmpty() }
    }

    private fun isValidPath(
        path: List<NodeAncestorProjection>,
        modelId: UUID,
        configuredRootId: UUID?
    ): Boolean {
        if (
            path.any(NodeAncestorProjection::getCycle) ||
            path.any { it.getDepth() > MAX_DEPTH } ||
            path.any { it.getModelId() != modelId }
        ) {
            return false
        }
        if (configuredRootId != null) {
            return path.any { it.getId() == configuredRootId }
        }
        val terminal = path.maxBy(NodeAncestorProjection::getDepth)
        return terminal.getParentNodeId() == null || terminal.getHiddenTreeRoot()
    }

    private fun readConfiguredRootId(attrs: String?): UUID? {
        val rootNodeId = try {
            attrs?.let(objectMapper::readTree)?.get("treeRootNodeId") ?: return null
        } catch (_: Exception) {
            return null
        }
        if (!rootNodeId.isTextual) {
            return null
        }
        val rawRootId = rootNodeId.asText().trim()
        if (rawRootId.isEmpty()) {
            return null
        }
        return try {
            UUID.fromString(rawRootId)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val MAX_DEPTH = 256
    }
}
