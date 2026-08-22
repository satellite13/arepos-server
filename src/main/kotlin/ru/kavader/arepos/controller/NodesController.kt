package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.EnsureNodeResponse
import ru.kavader.arepos.dto.model.NodeRequest
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.dto.model.NodeUpdateRequest
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.DiagramCanvasInstancesCleanupService
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.ModelSyncBroadcaster
import ru.kavader.arepos.service.ModelTreeQueryService
import ru.kavader.arepos.service.NodeEnsureService
import java.time.Instant
import java.util.*

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
    private val modelMapper: ModelMapper,
    private val nodeEnsureService: NodeEnsureService,
    private val modelTreeQueryService: ModelTreeQueryService
) {

    @GetMapping
    @Operation(summary = "List nodes")
    fun listNodes(
        pageable: Pageable,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) parentId: String?,
        @RequestParam(defaultValue = "true") excludeSystem: Boolean,
        @RequestParam(defaultValue = "false") foldersOnly: Boolean
    ): Page<NodeResponse> {
        if (parentId != null) {
            val scopedModelId = modelId ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "modelId is required when parentId is provided"
            )
            return modelTreeQueryService.listChildren(
                modelId = scopedModelId,
                parentRef = parentId,
                excludeSystem = excludeSystem,
                foldersOnly = foldersOnly,
                pageable = pageable
            )
        }

        val normalizedName = name.trimmedOrNull()
        if (!accessService.canViewAdminPanel()) {
            return nodesRepository.findAccessibleByFiltersForUser(
                modelId = modelId,
                ownerId = ownerId,
                name = normalizedName,
                currentUserId = accessService.currentUserId(),
                pageable = pageable
            )
                .applyMcpModelAllowlist(accessService, modelId) { it.model.id }
                .map { modelMapper.toResponse(it) }
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
    fun createNode(@RequestBody @Valid request: NodeRequest): NodeResponse =
        nodeEnsureService.createNode(request)

    @PostMapping("/ensure")
    @Operation(summary = "Find or create node by model/parent/name (case-insensitive)")
    fun ensureNode(@RequestBody @Valid request: NodeRequest): EnsureNodeResponse =
        nodeEnsureService.ensureNode(request)

    @PutMapping("/{id}")
    @Operation(summary = "Update node")
    fun updateNode(
        @PathVariable id: UUID,
        @RequestBody @Valid request: NodeUpdateRequest
    ): NodeResponse {
        val node = nodesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")
            }
        if (isSystemTreeRoot(node)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "System tree root node cannot be modified")
        }
        accessService.requireCanEditNode(node)

        val (owner, model) = ModelBoundEntityUpdateSupport.resolveOwnerAndModel(
            requestOwnerId = request.ownerId,
            requestModelId = request.modelId,
            currentOwner = node.owner,
            currentModel = node.model,
            ownerResolutionService = ownerResolutionService,
            modelsRepository = modelsRepository,
            accessService = accessService
        )

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

        node.name = request.name ?: node.name
        node.model = model
        node.owner = owner
        node.nodeType = nodeType
        node.parentNode = parentNode
        node.attrs = request.attrs ?: node.attrs
        val updated = nodesRepository.save(node)
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
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.NODE_DELETED.wireValue,
                    ModelSyncEventType.NODE_DELETED.entity,
                    id
                )
            )
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

