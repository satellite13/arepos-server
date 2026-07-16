package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.ModelRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import java.time.Instant
import java.util.UUID

@Service
class ModelLifecycleService(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val modelAttrsService: ModelAttrsService,
    private val systemRootNodeTypeService: SystemRootNodeTypeService,
    private val resourceSharesRepository: ResourceSharesRepository
) {
    companion object {
        private const val SYSTEM_ROOT_NODE_NAME = "Root"
    }

    @Transactional
    fun createModel(request: ModelRequest, owner: Users): Models {
        val now = Instant.now()
        val saved = modelsRepository.save(
            Models(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                version = request.version,
                owner = owner,
                deleted = false
            )
        )
        val rootNodeType = systemRootNodeTypeService.getOrCreate(owner, now)
        val rootNode = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = SYSTEM_ROOT_NODE_NAME,
                createdAt = now,
                updatedAt = now,
                attrs = """{"system":{"hiddenTreeRoot":true},"treeOrder":0}""",
                parentNode = null,
                model = saved,
                owner = owner,
                nodeType = rootNodeType
            )
        )
        saved.attrs = modelAttrsService.mergeWithTreeRootNodeId(saved.attrs, requireNotNull(rootNode.id))
        return modelsRepository.save(saved)
    }

    @Transactional
    fun permanentDeleteModel(model: Models) {
        val modelId = model.id
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Model id is required")
        resourceSharesRepository.deleteByResourceTypeAndResourceId(ShareResourceType.MODEL, modelId)
        modelsRepository.delete(model)
    }

    @Transactional
    fun softDeleteModel(id: UUID) {
        val deletedCount = modelsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
        }
    }
}
