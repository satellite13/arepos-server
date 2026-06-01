package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.notation.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.OwnerResolutionService
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
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val notationMapper: NotationMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(NodeTypesController::class.java)
    }
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

    @GetMapping
    fun listNodeTypes(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) notationId: List<UUID>?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeTypeResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            val normalizedName = name?.trim().orEmpty()
            if (notationId.isNullOrEmpty() && modelId == null) {
                return nodeTypesRepository.findAccessibleForUser(
                    userId = currentUserId,
                    ownerId = ownerId,
                    name = normalizedName,
                    viewPermissions = viewPermissions,
                    pageable = pageable
                ).map { notationMapper.toResponse(it) }
            }

            val resolvedModel = modelId?.let { mid ->
                modelsRepository.findById(mid).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Model $mid not found")
                }.also { accessService.requireCanViewModel(it) }
            }
            val notationOwnerIds = mutableSetOf<UUID>()
            val notationNodeTypeIds = mutableSetOf<UUID>()
            notationId?.forEach { requestedNotationId ->
                val notation = notationsRepository.findById(requestedNotationId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $requestedNotationId not found")
                }
                val allowed = when (val m = resolvedModel) {
                    null -> accessService.canViewNotation(notation)
                    else -> accessService.canUseNotationInModelDiagramEditor(notation, m)
                }
                if (!allowed) {
                    return@forEach
                }
                componentsRepository.findDistinctNodeTypeIdsByNotationId(requestedNotationId)
                    .forEach { notationNodeTypeIds.add(it) }
                notation.owner.id?.let { notationOwnerIds.add(it) }
            }
            val modelNodeTypeIds = resolvedModel?.let { model ->
                nodesRepository.findDistinctNodeTypeIdsByModelId(model.id!!)
                    .toSet()
            } ?: emptySet()
            val accessible = nodeTypesRepository.findAccessibleForUser(
                userId = currentUserId,
                ownerId = ownerId,
                name = normalizedName,
                viewPermissions = viewPermissions,
                pageable = Pageable.unpaged()
            ).content
            val ownerMatched = if (notationOwnerIds.isEmpty()) {
                emptyList()
            } else {
                nodeTypesRepository.findByOwnerIdIn(notationOwnerIds)
            }
            val idMatchedIds = notationNodeTypeIds + modelNodeTypeIds
            val idMatched = if (idMatchedIds.isEmpty()) {
                emptyList()
            } else {
                nodeTypesRepository.findByIdIn(idMatchedIds)
            }
            val filtered = (accessible + ownerMatched + idMatched)
                .asSequence()
                .distinctBy { it.id }
                .filter { ownerId == null || it.owner.id == ownerId }
                .filter { normalizedName.isEmpty() || it.name.contains(normalizedName, ignoreCase = true) }
                .toList()
            return filtered.toPage(pageable).map { notationMapper.toResponse(it) }
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
        return nodeTypes.map { notationMapper.toResponse(it) }
    }

    @GetMapping("/{id}")
    fun getNodeType(@PathVariable id: UUID): NodeTypeResponse =
        nodeTypesRepository.findById(id)
            .map {
                if (!accessService.canViewNodeType(it) && !accessService.canUseNodeType(it)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                }
                notationMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNodeType(@RequestBody request: NodeTypeRequest): NodeTypeResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
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
        return notationMapper.toResponse(saved)
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
        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, nodeType.owner)

        val updated = nodeTypesRepository.save(
            nodeType.copy(
                name = request.name ?: nodeType.name,
                attrs = request.attrs ?: nodeType.attrs,
                owner = owner
            )
        )
        return notationMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNodeType(@PathVariable id: UUID) {
        val nodeType = nodeTypesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
        }
        accessService.requireCanEditNodeType(nodeType)
        nodeTypesRepository.deleteById(id)
    }

    private fun resolveReadableOwner(ownerId: UUID?): ru.kavader.arepos.model.Users? =
        ownerResolutionService.resolveReadableOwner(ownerId) { oid, uid ->
            nodeTypesRepository.findAccessibleForUser(uid, oid, "", viewPermissions, Pageable.ofSize(1)).hasContent()
        }

}
