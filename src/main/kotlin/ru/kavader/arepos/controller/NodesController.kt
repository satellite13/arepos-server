package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nodes")
class NodesController(
    private val nodesRepository: NodesRepository,
    private val modelsRepository: ModelsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val diagramsRepository: DiagramsRepository,
    private val componentsRepository: ComponentsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val objectMapper: ObjectMapper,
    private val mdFileLinkValidator: MdFileLinkValidator
) {

    @GetMapping
    fun listNodes(
        pageable: Pageable,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            return nodesRepository.findAccessibleByFiltersForUser(
                modelId = modelId,
                ownerId = ownerId,
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                currentUserId = currentUserId,
                pageable = pageable
            ).map { it.toResponse() }
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
        return nodes.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getNode(@PathVariable id: UUID): NodeResponse =
        nodesRepository.findById(id)
            .map {
                accessService.requireCanViewNode(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNode(@RequestBody request: NodeRequest): NodeResponse {
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        accessService.requireCanEditModel(model)
        val currentUserId = accessService.currentUserId()
        val resolvedOwnerId = if (accessService.canViewAdminPanel()) {
            request.ownerId ?: currentUserId
        } else {
            currentUserId
        }
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
        val nodeType = nodeTypesRepository.findById(request.nodeTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType ${request.nodeTypeId} not found")
            }
        requireCanUseNodeTypeForModel(nodeType, model)
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
        return saved.toResponse()
    }

    @PutMapping("/{id}")
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

        val owner = if (accessService.canViewAdminPanel()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: node.owner
        } else {
            node.owner
        }

        val nodeType = request.nodeTypeId?.let {
            nodeTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $it not found")
            }
        }?.also { newNodeType ->
            requireCanUseNodeTypeForModel(newNodeType, model)
        } ?: node.nodeType

        mdFileLinkValidator.validate(request.attrs)
        val parentNode = if (request.parentNodeId != null) {
            val parentId = request.parentNodeId
            if (parentId == id) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Node cannot be its own parent")
            }
            nodesRepository.findById(parentId).orElse(null)?.also { parent ->
                accessService.requireCanEditNode(parent)
            }
        } else {
            null
        }

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
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
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
        nodesRepository.deleteById(id)
    }

    private fun getCurrentUser() = CurrentUser.getId()?.let {
        usersRepository.findById(it).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $it not found")
        }
    } ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

    private fun Nodes.toResponse() = NodeResponse(
        id = requireNotNull(id),
        stableId = stableId,
        name = name,
        modelId = model.id!!,
        ownerId = owner.id!!,
        nodeTypeId = nodeType.id!!,
        parentNodeId = parentNode?.id,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun requireCanUseNodeTypeForModel(
        nodeType: ru.kavader.arepos.model.NodeTypes,
        model: ru.kavader.arepos.model.Models
    ) {
        if (accessService.canUseNodeType(nodeType)) return
        val canEditModel = accessService.canEditModel(model)
        if (canEditModel && nodeType.owner.id == model.owner.id) return
        if (
            canEditModel &&
                isNodeTypeUsedInModelDiagramNotations(requireNotNull(nodeType.id), model)
        ) {
            return
        }
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
    }

    private fun isNodeTypeUsedInModelDiagramNotations(
        nodeTypeId: UUID,
        model: ru.kavader.arepos.model.Models
    ): Boolean {
        val notationIds = diagramsRepository.findDistinctNotationIdsByModelId(requireNotNull(model.id)).toSet()
        if (notationIds.isEmpty()) return false

        return componentsRepository.existsByNodeType_IdAndNotation_IdIn(nodeTypeId, notationIds)
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

data class NodeRequest(
    val name: String,
    val modelId: UUID,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID,
    val parentNodeId: UUID? = null,
    val attrs: String? = null,
    val stableId: UUID? = null
)

data class NodeUpdateRequest(
    val name: String? = null,
    val modelId: UUID? = null,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val parentNodeId: UUID? = null,
    val attrs: String? = null
)

data class NodeResponse(
    val id: UUID,
    val stableId: UUID,
    val name: String,
    val modelId: UUID,
    val ownerId: UUID,
    val nodeTypeId: UUID,
    val parentNodeId: UUID?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
