package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
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
@RequestMapping("/api/v1/models")
class ModelsController(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val usersRepository: UsersRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val accessService: ResourceAccessService,
    private val objectMapper: ObjectMapper,
    private val mdFileLinkValidator: MdFileLinkValidator
) {
    companion object {
        private const val SYSTEM_ROOT_NODE_TYPE_NAME = "Directory"
        private const val SYSTEM_ROOT_NODE_NAME = "Root"
    }

    @GetMapping
    fun listModels(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<ModelResponse> {
        if (!CurrentUser.isAdmin()) {
            val filtered = modelsRepository.findAll(Pageable.unpaged()).content
                .asSequence()
                .filter { accessService.canViewModel(it) }
                .filter { ownerId == null || it.owner.id == ownerId }
                .filter { name == null || it.name.contains(name, ignoreCase = true) }
                .toList()
            return filtered.toPage(pageable).map { it.toResponse() }
        }

        val effectiveOwner = resolveReadableOwner(ownerId)
        val models = when {
            effectiveOwner != null && name != null ->
                modelsRepository.findByOwnerAndNameContainingIgnoreCase(effectiveOwner, name, pageable)
            effectiveOwner != null ->
                modelsRepository.findByOwner(effectiveOwner, pageable)
            name != null ->
                modelsRepository.findByNameContainingIgnoreCase(name, pageable)
            else ->
                modelsRepository.findAll(pageable)
        }
        return models.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getModel(@PathVariable id: UUID): ModelResponse =
        modelsRepository.findById(id)
            .map {
                accessService.requireCanViewModel(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createModel(@RequestBody request: ModelRequest): ModelResponse {
        if (modelsRepository.existsByNameAndVersion(request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '${request.name}' and version '${request.version}' already exists"
            )
        }
        val currentUserId = accessService.currentUserId()
        val resolvedOwnerId = if (CurrentUser.isAdmin()) {
            request.ownerId ?: currentUserId
        } else {
            currentUserId
        }
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
        mdFileLinkValidator.validate(request.attrs)
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
        val rootNodeType = getOrCreateSystemRootNodeType(owner, now)
        val rootNode = nodesRepository.save(
            Nodes(
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
        val attrsWithRoot = mergeModelAttrsWithRootNodeId(saved.attrs, requireNotNull(rootNode.id))
        val updatedModel = modelsRepository.save(saved.copy(attrs = attrsWithRoot))
        return updatedModel.toResponse()
    }

    @PutMapping("/{id}")
    fun updateModel(
        @PathVariable id: UUID,
        @RequestBody request: ModelUpdateRequest
    ): ModelResponse {
        val model = modelsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        accessService.requireCanEditModel(model)
        mdFileLinkValidator.validate(request.attrs)

        val newName = request.name ?: model.name
        val newVersion = request.version ?: model.version
        if (modelsRepository.existsByNameAndVersionAndIdNot(newName, newVersion, id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '$newName' and version '$newVersion' already exists"
            )
        }
        val owner = if (CurrentUser.isAdmin()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: model.owner
        } else {
            model.owner
        }

        val updated = modelsRepository.save(
            model.copy(
                name = newName,
                attrs = request.attrs ?: model.attrs,
                version = newVersion,
                owner = owner
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun deleteModel(@PathVariable id: UUID) {
        val model = modelsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        accessService.requireCanEditModel(model)
        val deletedCount = modelsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
        }
    }

    @PostMapping("/{sourceId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun copyModel(
        @PathVariable sourceId: UUID,
        @RequestBody request: ModelRequest
    ): ModelResponse {
        val source = modelsRepository.findById(sourceId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Source model $sourceId not found")
            }
        accessService.requireCanEditModel(source)
        if (modelsRepository.existsByNameAndVersion(request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '${request.name}' and version '${request.version}' already exists"
            )
        }
        val currentUserId = accessService.currentUserId()
        val resolvedOwnerId = if (CurrentUser.isAdmin()) {
            request.ownerId ?: currentUserId
        } else {
            currentUserId
        }
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()

        val newModel = modelsRepository.save(
            Models(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = source.attrs,
                version = request.version,
                owner = owner,
                source = source,
                deleted = false
            )
        )

        val rootNodeType = getOrCreateSystemRootNodeType(owner, now)
        val sourceNodes = nodesRepository.findByModelIdOrdered(source.id!!, Pageable.unpaged()).content
        val nodeIdMap = mutableMapOf<UUID, UUID>()
        val sourceRoot = sourceNodes.firstOrNull { it.parentNode == null }
            ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Source model has no root node")

        val newRoot = nodesRepository.save(
            Nodes(
                name = SYSTEM_ROOT_NODE_NAME,
                createdAt = now,
                updatedAt = now,
                attrs = sourceRoot.attrs,
                parentNode = null,
                model = newModel,
                owner = owner,
                nodeType = rootNodeType
            )
        )
        nodeIdMap[sourceRoot.id!!] = newRoot.id!!
        val attrsWithRoot = mergeModelAttrsWithRootNodeId(newModel.attrs, newRoot.id!!)
        modelsRepository.save(newModel.copy(attrs = attrsWithRoot))

        val pendingNodes = sourceNodes.filter { it.id != sourceRoot.id }.toMutableList()
        while (pendingNodes.isNotEmpty()) {
            var progress = false
            val iterator = pendingNodes.iterator()
            while (iterator.hasNext()) {
                val srcNode = iterator.next()
                val newParentId = srcNode.parentNode?.id?.let { nodeIdMap[it] } ?: continue
                val newParent = nodesRepository.findById(newParentId).orElseThrow { IllegalStateException("New parent not found") }
                val saved = nodesRepository.save(
                    srcNode.copy(
                        id = null,
                        parentNode = newParent,
                        model = newModel,
                        owner = owner,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                nodeIdMap[srcNode.id!!] = saved.id!!
                iterator.remove()
                progress = true
            }
            if (!progress) {
                val unmapped = pendingNodes.firstOrNull()
                throw IllegalStateException(
                    "Parent node not yet mapped for node ${unmapped?.id}. " +
                        "Check that all nodes have a path to root (no cycles or broken parent links)."
                )
            }
        }

        val sourceLinks = linksRepository.findByModel(source, Pageable.unpaged()).content
        for (srcLink in sourceLinks) {
            val newSourceId = nodeIdMap[srcLink.source.id!!]
                ?: continue
            val newTargetId = nodeIdMap[srcLink.target.id!!]
                ?: continue
            val newSource = nodesRepository.findById(newSourceId).orElseThrow { IllegalStateException("New source node not found") }
            val newTarget = nodesRepository.findById(newTargetId).orElseThrow { IllegalStateException("New target node not found") }
            linksRepository.save(
                Links(
                    source = newSource,
                    target = newTarget,
                    attrs = srcLink.attrs,
                    createdAt = now,
                    updatedAt = now,
                    owner = owner,
                    linkType = srcLink.linkType,
                    model = newModel
                )
            )
        }

        val sourceDiagrams = diagramsRepository.findByFilters(
            ownerId = null,
            modelId = source.id,
            nodeId = null,
            notationId = null,
            name = "",
            pageable = Pageable.unpaged()
        ).content
        for (srcDiagram in sourceDiagrams) {
            val newNodeId = srcDiagram.node?.id?.let { nodeIdMap[it] }?.let { nodesRepository.findById(it).orElse(null) }
            val remappedAttrs = remapDiagramAttrsNodeIds(srcDiagram.attrs, nodeIdMap)
            diagramsRepository.save(
                Diagrams(
                    name = srcDiagram.name,
                    version = srcDiagram.version,
                    createdAt = now,
                    updatedAt = now,
                    attrs = remappedAttrs,
                    owner = owner,
                    model = newModel,
                    notation = srcDiagram.notation,
                    node = newNodeId,
                    deleted = false
                )
            )
        }

        return modelsRepository.findById(newModel.id!!).orElseThrow { IllegalStateException("New model not found") }.toResponse()
    }

    private fun remapDiagramAttrsNodeIds(attrs: String?, nodeIdMap: Map<UUID, UUID>): String? {
        if (attrs.isNullOrBlank()) return attrs
        return try {
            val tree = objectMapper.readTree(attrs) ?: return attrs
            val instances = tree.get("instances") ?: return objectMapper.writeValueAsString(tree)
            val nodes = instances.get("nodes") ?: return objectMapper.writeValueAsString(tree)
            for (i in 0 until nodes.size()) {
                val node = nodes.get(i) ?: continue
                val modelNodeId = node.get("modelNodeId")?.asText() ?: continue
                val oldUuid = try {
                    UUID.fromString(modelNodeId)
                } catch (_: Exception) {
                    continue
                }
                val newUuid = nodeIdMap[oldUuid] ?: continue
                (node as? ObjectNode)?.put("modelNodeId", newUuid.toString())
            }
            objectMapper.writeValueAsString(tree)
        } catch (_: Exception) {
            attrs
        }
    }

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isAdmin()) {
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
            // Non-admin users can filter by owner only if they have shared access from that owner.
            val hasSharedFromOwner = modelsRepository.findAll(Pageable.unpaged()).content.any {
                it.owner.id == ownerId && accessService.canViewModel(it)
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

    private fun Models.toResponse() = ModelResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        ownerId = owner.id!!,
        accessPermission = accessService.modelAccessPermission(this),
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sourceId = source?.id
    )

    private fun getOrCreateSystemRootNodeType(
        owner: ru.kavader.arepos.model.Users,
        now: Instant
    ): NodeTypes =
        nodeTypesRepository.findByNameIgnoreCase(SYSTEM_ROOT_NODE_TYPE_NAME)
            ?: nodeTypesRepository.save(
                NodeTypes(
                    name = SYSTEM_ROOT_NODE_TYPE_NAME,
                    createdAt = now,
                    updatedAt = now,
                    attrs = """{"system":{"hiddenTreeRootType":true}}""",
                    owner = owner
                )
            )

    private fun mergeModelAttrsWithRootNodeId(existingAttrs: String?, rootNodeId: UUID): String {
        val rootId = rootNodeId.toString()
        val baseNode = try {
            existingAttrs
                ?.takeIf { it.isNotBlank() }
                ?.let { objectMapper.readTree(it) }
                ?.takeIf { it.isObject }
                ?.deepCopy<ObjectNode>()
                ?: objectMapper.createObjectNode()
        } catch (_: Exception) {
            objectMapper.createObjectNode()
        }
        baseNode.put("treeRootNodeId", rootId)
        return objectMapper.writeValueAsString(baseNode)
    }
}

data class ModelRequest(
    val name: String,
    val version: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class ModelUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class ModelResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val sourceId: UUID? = null
)
