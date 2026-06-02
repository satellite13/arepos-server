package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.ModelMapper
import ru.kavader.arepos.dto.model.*
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.DiagramCanvasInstancesCleanupService
import ru.kavader.arepos.service.MdFileLinkValidator
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import ru.kavader.arepos.service.ModelSyncBroadcaster
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nodes")
@Tag(name = "Nodes", description = "Model nodes management endpoints")
class NodesController(
    private val nodesRepository: NodesRepository,
    private val modelsRepository: ModelsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val objectMapper: ObjectMapper,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val diagramCanvasInstancesCleanupService: DiagramCanvasInstancesCleanupService,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val typeUsageAuthorization: TypeUsageAuthorization,
    private val modelMapper: ModelMapper
) {

    @GetMapping
    @Operation(summary = "List nodes")
    fun listNodes(
        pageable: Pageable,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeResponse> {
        val normalizedName = name.trimmedOrNull()
        if (!accessService.canViewAdminPanel()) {
            return nodesRepository.findAccessibleByFiltersForUser(
                modelId = modelId,
                ownerId = ownerId,
                name = normalizedName,
                currentUserId = accessService.currentUserId(),
                pageable = pageable
            ).map { modelMapper.toResponse(it) }
        }

        val nodes = when {
            modelId != null -> {
                val model = modelsRepository.findById(modelId).orElse(null)
                if (model != null) {
                    nodesRepository.findByModelIdOrdered(model.id!!, pageable)
                } else {
                    nodesRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    nodesRepository.findByOwner(owner, pageable)
                } else {
                    nodesRepository.findAll(pageable)
                }
            }
            name != null -> {
                nodesRepository.findByNameContainingIgnoreCase(name, pageable)
            }
            else -> {
                nodesRepository.findAll(pageable)
            }
        }
        return nodes.map { modelMapper.toResponse(it) }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get node by id")
    fun getNode(@PathVariable id: UUID): NodeResponse =
        nodesRepository.findById(id)
            .map {
                accessService.requireCanViewNode(it)
                modelMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")
            }

    @PostMapping
    @Operation(summary = "Create node")
    @ResponseStatus(HttpStatus.CREATED)
    fun createNode(@RequestBody @Valid request: NodeRequest): NodeResponse {
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        accessService.requireCanEditModel(model)
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val nodeType = nodeTypesRepository.findById(request.nodeTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType ${request.nodeTypeId} not found")
            }
        typeUsageAuthorization.requireCanUseNodeTypeForModel(nodeType, model)
        val parentNode = request.parentNodeId?.let {
            nodesRepository.findById(it).orElse(null)?.also { parent ->
                accessService.requireCanEditNode(parent)
            }
        }

        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = nodesRepository.save(
            Nodes(
                stableId = request.stableId ?: UUID.randomUUID(),
                name = request.name,
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = parentNode,
                attrs = request.attrs,
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
        return modelMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update node")
    fun updateNode(
        @PathVariable id: UUID,
        @RequestBody request: NodeUpdateRequest
    ): NodeResponse {
        val node = nodesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")
            }
        if (isSystemTreeRoot(node)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "System tree root node cannot be modified")
        }
        accessService.requireCanEditNode(node)

        val model = request.modelId?.let {
            modelsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $it not found")
            }
        }?.also { newModel ->
            accessService.requireCanEditModel(newModel)
        } ?: node.model

        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, node.owner)

        val nodeType = request.nodeTypeId?.let {
            nodeTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $it not found")
            }
        }?.also { newNodeType ->
            typeUsageAuthorization.requireCanUseNodeTypeForModel(newNodeType, model)
        } ?: node.nodeType

        mdFileLinkValidator.validate(request.attrs)
        val parentNode = request.parentNodeId?.let { parentId ->
            if (parentId == id) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Node cannot be its own parent")
            }
            nodesRepository.findById(parentId).orElse(null)?.also { parent ->
                accessService.requireCanEditNode(parent)
            }
        } ?: node.parentNode

        val updated = nodesRepository.save(
            node.copy(
                name = request.name ?: node.name,
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = parentNode,
                attrs = request.attrs ?: node.attrs
            )
        )
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(model.id),
            ModelSyncChangeType.NODE_UPDATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.NODE_UPDATED.wireValue,
                    ModelSyncEventType.NODE_UPDATED.entity,
                    requireNotNull(updated.id)
                )
            )
        )
        return modelMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete node")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNode(@PathVariable id: UUID) {
        val node = nodesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")
            }
        if (isSystemTreeRoot(node)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "System tree root node cannot be deleted")
        }
        accessService.requireCanEditNode(node)
        val modelId = requireNotNull(node.model.id)
        nodesRepository.deleteById(id)
        diagramCanvasInstancesCleanupService.removeDeletedModelEntitiesFromAllDiagrams(
            modelId,
            listOf(id),
            emptyList(),
            Instant.now()
        )
        modelSyncBroadcaster.broadcastModelChanged(
            modelId,
            ModelSyncChangeType.NODE_DELETE.wireValue,
            listOf(ModelSyncEntityEvent(ModelSyncEventType.NODE_DELETED.wireValue, ModelSyncEventType.NODE_DELETED.entity, id))
        )
    }

    private fun isSystemTreeRoot(node: Nodes): Boolean {
        val attrs = node.attrs ?: return false
        return try {
            val root = objectMapper.readTree(attrs)
            root.path("system").path("hiddenTreeRoot").asBoolean(false)
        } catch (_: Exception) {
            false
        }
    }

}

