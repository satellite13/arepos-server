package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@Service
class ModelTreeQueryService(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val accessService: ResourceAccessService,
    private val objectMapper: ObjectMapper,
    private val treePageReader: ModelTreePageReader
) {
    fun listChildren(
        modelId: UUID,
        parentRef: String,
        excludeSystem: Boolean,
        foldersOnly: Boolean,
        pageable: Pageable
    ): Page<NodeResponse> {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)

        val parentNodeId = resolveParentNodeId(model, parentRef)
        val boundedPageable = PageRequest.of(pageable.pageNumber, pageable.pageSize.coerceIn(1, MAX_PAGE_SIZE))
        return treePageReader.readPage(
            modelId = modelId,
            parentNodeId = parentNodeId,
            excludeSystem = excludeSystem,
            foldersOnly = foldersOnly,
            pageable = boundedPageable
        )
    }

    private fun resolveParentNodeId(model: Models, parentRef: String): UUID? {
        if (parentRef == ROOT_PARENT_REF) {
            val configuredRootId = configuredRootId(model) ?: return null
            val configuredRoot = nodesRepository.findById(configuredRootId).orElse(null)
            if (configuredRoot?.model?.id != model.id) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Configured model tree root is missing")
            }
            return configuredRootId
        }

        val parentId = try {
            UUID.fromString(parentRef)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "parentId must be 'root' or a UUID")
        }
        val parent = nodesRepository.findById(parentId).orElse(null)
        if (parent?.model?.id != model.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Parent node $parentId not found")
        }
        return parentId
    }

    private fun configuredRootId(model: Models): UUID? {
        val attrs = model.attrs ?: return null
        val rootNodeId = try {
            objectMapper.readTree(attrs).get("treeRootNodeId") ?: return null
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Model tree root configuration is invalid")
        }
        if (!rootNodeId.isTextual) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Model tree root configuration is invalid")
        }
        val rawId = rootNodeId.asText().trim()
        if (rawId.isEmpty()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Model tree root configuration is invalid")
        }
        return try {
            UUID.fromString(rawId)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Model tree root configuration is invalid")
        }
    }

    private companion object {
        const val ROOT_PARENT_REF = "root"
        const val MAX_PAGE_SIZE = 500
    }
}
