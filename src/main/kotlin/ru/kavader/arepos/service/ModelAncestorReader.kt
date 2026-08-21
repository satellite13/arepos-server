package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeAncestorProjection
import ru.kavader.arepos.repository.NodesRepository
import java.util.UUID

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class ModelAncestorReader(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val objectMapper: ObjectMapper
) {
    fun readAncestors(modelId: UUID, nodeId: UUID): List<NodeResponse> {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        val configuredRootId = configuredRootId(model.attrs)
        val path = nodesRepository.findAncestorPath(
            modelId = modelId,
            nodeId = nodeId,
            configuredRootId = configuredRootId,
            maxDepthPlusOne = MAX_DEPTH + 1
        )
        if (path.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Node $nodeId not found")
        }

        validatePath(path, modelId, configuredRootId)
        return path.asSequence()
            .filter { it.getDepth() > 0 }
            .filterNot { it.getHiddenTreeRoot() || it.getId() == configuredRootId }
            .map(NodeAncestorProjection::toResponse)
            .toList()
    }

    private fun validatePath(
        path: List<NodeAncestorProjection>,
        modelId: UUID,
        configuredRootId: UUID?
    ) {
        if (
            path.any(NodeAncestorProjection::getCycle) ||
            path.any { it.getDepth() > MAX_DEPTH } ||
            path.any { it.getModelId() != modelId }
        ) {
            invalidPath()
        }

        if (configuredRootId != null) {
            if (path.none { it.getId() == configuredRootId }) {
                invalidPath()
            }
            return
        }

        val terminal = path.maxBy(NodeAncestorProjection::getDepth)
        if (terminal.getParentNodeId() != null && !terminal.getHiddenTreeRoot()) {
            invalidPath()
        }
    }

    private fun configuredRootId(attrs: String?): UUID? {
        val rootNodeId = try {
            attrs?.let(objectMapper::readTree)?.get("treeRootNodeId") ?: return null
        } catch (_: Exception) {
            invalidRootConfiguration()
        }
        if (!rootNodeId.isTextual) {
            invalidRootConfiguration()
        }
        return try {
            UUID.fromString(rootNodeId.asText().trim())
        } catch (_: IllegalArgumentException) {
            invalidRootConfiguration()
        }
    }

    private fun invalidPath(): Nothing =
        throw ResponseStatusException(HttpStatus.CONFLICT, "Model ancestor path is invalid")

    private fun invalidRootConfiguration(): Nothing =
        throw ResponseStatusException(HttpStatus.CONFLICT, "Model tree root configuration is invalid")

    private companion object {
        const val MAX_DEPTH = 256
    }
}

private fun NodeAncestorProjection.toResponse() = NodeResponse(
    id = getId(),
    stableId = getStableId(),
    name = getName(),
    modelId = getModelId(),
    ownerId = getOwnerId(),
    nodeTypeId = getNodeTypeId(),
    parentNodeId = getParentNodeId(),
    attrs = getAttrs(),
    createdAt = getCreatedAt(),
    updatedAt = getUpdatedAt(),
    hasChildren = getHasChildren()
)
