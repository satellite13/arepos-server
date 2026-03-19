package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/node-types")
class NodeTypesController(
    private val nodeTypesRepository: NodeTypesRepository,
    private val usersRepository: UsersRepository,
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val accessService: ResourceAccessService,
    private val mdFileLinkValidator: MdFileLinkValidator
) {
    companion object {
        private val log = LoggerFactory.getLogger(NodeTypesController::class.java)
    }

    @GetMapping
    fun listNodeTypes(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) notationId: List<UUID>?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeTypeResponse> {
        if (!CurrentUser.isAdmin()) {
            val notationOwnerIds = mutableSetOf<UUID>()
            val notationNodeTypeIds = mutableSetOf<UUID>()
            notationId?.forEach { requestedNotationId ->
                val notation = notationsRepository.findById(requestedNotationId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $requestedNotationId not found")
                }
                accessService.requireCanViewNotation(notation)
                componentsRepository.findByNotation(notation, Pageable.unpaged()).content
                    .mapNotNull { it.nodeType.id }
                    .forEach { notationNodeTypeIds.add(it) }
                notation.owner.id?.let { notationOwnerIds.add(it) }
            }
            val modelNodeTypeIds = modelId?.let { requestedModelId ->
                val model = modelsRepository.findById(requestedModelId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Model $requestedModelId not found")
                }
                accessService.requireCanViewModel(model)
                nodesRepository.findByModelIdOrdered(model.id!!, Pageable.unpaged()).content
                    .asSequence()
                    .mapNotNull { it.nodeType.id }
                    .toSet()
            } ?: emptySet()
            val filtered = nodeTypesRepository.findAll(Pageable.unpaged()).content
                .asSequence()
                .filter {
                    accessService.canViewNodeType(it) ||
                        accessService.canUseNodeType(it) ||
                        notationOwnerIds.contains(it.owner.id) ||
                        notationNodeTypeIds.contains(it.id) ||
                        modelNodeTypeIds.contains(it.id)
                }
                .filter { ownerId == null || it.owner.id == ownerId }
                .filter { name == null || it.name.contains(name, ignoreCase = true) }
                .toList()
            return filtered.toPage(pageable).map { it.toResponse() }
        }

        val effectiveOwner = resolveReadableOwner(ownerId)
        val nodeTypes = when {
            effectiveOwner != null && name != null ->
                nodeTypesRepository.findByOwnerAndNameContainingIgnoreCase(effectiveOwner, name, pageable)
            effectiveOwner != null ->
                nodeTypesRepository.findByOwner(effectiveOwner, pageable)
            name != null ->
                nodeTypesRepository.findByNameContainingIgnoreCase(name, pageable)
            else ->
                nodeTypesRepository.findAll(pageable)
        }
        return nodeTypes.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getNodeType(@PathVariable id: UUID): NodeTypeResponse =
        nodeTypesRepository.findById(id)
            .map {
                if (!accessService.canViewNodeType(it) && !accessService.canUseNodeType(it)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                }
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNodeType(@RequestBody request: NodeTypeRequest): NodeTypeResponse {
        val currentUserId = accessService.currentUserId()
        if (!CurrentUser.isAdmin() && request.ownerId != null && request.ownerId != currentUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create node type for another user")
        }
        val resolvedOwnerId = if (CurrentUser.isAdmin()) {
            request.ownerId ?: currentUserId
        } else {
            currentUserId
        }
        log.info(
            "createNodeType request: currentUserId={}, role={}, requestOwnerId={}, resolvedOwnerId={}",
            CurrentUser.getId(),
            CurrentUser.getRole(),
            request.ownerId,
            resolvedOwnerId
        )
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = nodeTypesRepository.save(
            NodeTypes(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                owner = owner
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateNodeType(
        @PathVariable id: UUID,
        @RequestBody request: NodeTypeUpdateRequest
    ): NodeTypeResponse {
        val nodeType = nodeTypesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }
        accessService.requireCanEditNodeType(nodeType)
        val owner = if (CurrentUser.isAdmin()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: nodeType.owner
        } else {
            nodeType.owner
        }

        val updated = nodeTypesRepository.save(
            nodeType.copy(
                name = request.name ?: nodeType.name,
                attrs = request.attrs ?: nodeType.attrs,
                owner = owner
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNodeType(@PathVariable id: UUID) {
        val nodeType = nodeTypesRepository.findById(id).orElseThrow {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
        }
        accessService.requireCanEditNodeType(nodeType)
        nodeTypesRepository.deleteById(id)
    }

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isEditorOrAdmin()) {
            log.warn(
                "NodeTypes access denied: currentUserId={}, role={}, ownerId={}",
                currentUserId,
                CurrentUser.getRole(),
                ownerId
            )
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
    }

    private fun resolveReadableOwner(ownerId: UUID?): ru.kavader.arepos.model.Users? {
        val currentUserId = CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        if (CurrentUser.isAdmin()) {
            return ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            }
        }

        if (ownerId != null && ownerId != currentUserId) {
            val hasSharedFromOwner = nodeTypesRepository.findAll(Pageable.unpaged()).content.any {
                it.owner.id == ownerId && accessService.canViewNodeType(it)
            }
            if (!hasSharedFromOwner) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            }
            return null
        }

        return usersRepository.findById(currentUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $currentUserId not found")
        }
    }

    private fun NodeTypes.toResponse() = NodeTypeResponse(
        id = requireNotNull(id),
        name = name,
        ownerId = owner.id!!,
        accessPermission = accessService.nodeTypeAccessPermission(this),
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

}

data class NodeTypeRequest(
    val name: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NodeTypeUpdateRequest(
    val name: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NodeTypeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
