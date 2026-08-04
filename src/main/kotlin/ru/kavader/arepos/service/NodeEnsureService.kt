package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.AmbiguousNodeCandidate
import ru.kavader.arepos.dto.model.AmbiguousNodeException
import ru.kavader.arepos.dto.model.EnsureNodeResponse
import ru.kavader.arepos.dto.model.NodeRequest
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import java.time.Instant
import java.util.UUID

@Service
class NodeEnsureService(
    private val nodesRepository: NodesRepository,
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val typeUsageAuthorization: TypeUsageAuthorization,
    private val modelMapper: ModelMapper,
    private val notationBindingService: NotationBindingService
) {

    @Transactional
    fun createNode(request: NodeRequest): NodeResponse =
        createInternal(request).let { modelMapper.toResponse(it) }

    @Transactional
    fun ensureNode(request: NodeRequest): EnsureNodeResponse {
        val model = modelsRepository.findById(request.modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
        }
        accessService.requireCanEditModel(model)

        val matches = findMatches(request.modelId, request.parentNodeId, request.name)
        when (matches.size) {
            0 -> {
                val created = createInternal(request)
                return EnsureNodeResponse(node = modelMapper.toResponse(created), created = true)
            }
            1 -> {
                val existing = matches.first()
                accessService.requireCanViewNode(existing)
                return EnsureNodeResponse(node = modelMapper.toResponse(existing), created = false)
            }
            else -> {
                throw AmbiguousNodeException(
                    candidates = matches.map {
                        AmbiguousNodeCandidate(
                            id = requireNotNull(it.id),
                            name = it.name,
                            parentNodeId = it.parentNode?.id
                        )
                    }
                )
            }
        }
    }

    private fun findMatches(modelId: UUID, parentNodeId: UUID?, name: String): List<Nodes> =
        if (parentNodeId == null) {
            nodesRepository.findRootByModelIdAndNameIgnoreCase(modelId, name)
        } else {
            nodesRepository.findByModel_IdAndParentNode_IdAndNameIgnoreCase(modelId, parentNodeId, name)
        }

    private fun createInternal(request: NodeRequest): Nodes {
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        accessService.requireCanEditModel(model)
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val binding = notationBindingService.resolveNodeCreate(
            nodeTypeId = request.nodeTypeId,
            notationId = request.notationId,
            componentId = request.componentId,
            componentName = request.componentName,
            attrs = request.attrs
        )
        val nodeType = binding.nodeType
        typeUsageAuthorization.requireCanUseNodeTypeForModel(nodeType, model)
        val parentNode = request.parentNodeId?.let {
            nodesRepository.findById(it).orElse(null)?.also { parent ->
                accessService.requireCanEditNode(parent)
            }
        }

        mdFileLinkValidator.validate(binding.attrs)
        val now = Instant.now()
        val saved = nodesRepository.save(
            Nodes(
                stableId = request.stableId ?: UUID.randomUUID(),
                name = request.name,
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = parentNode,
                attrs = binding.attrs,
                createdAt = now,
                updatedAt = now
            )
        )
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(model.id),
            ModelSyncChangeType.NODE_CREATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.NODE_CREATED.wireValue,
                    ModelSyncEventType.NODE_CREATED.entity,
                    requireNotNull(saved.id)
                )
            )
        )
        return saved
    }
}
