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
import ru.kavader.arepos.model.SharePermission
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
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

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
            val currentUserId = accessService.currentUserId()
            return modelsRepository.findAccessibleForUser(
                userId = currentUserId,
                ownerId = ownerId,
                name = name?.trim().orEmpty(),
                viewPermissions = viewPermissions,
                pageable = pageable
            ).map { it.toResponse() }
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

    @GetMapping("/deleted")
    fun listDeletedModels(pageable: Pageable): Page<ModelResponse> {
        if (!CurrentUser.isAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")
        }
        return modelsRepository.findByDeletedTrue(pageable).map { it.toResponse() }
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun permanentDeleteModel(@PathVariable id: UUID) {
        if (!CurrentUser.isAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")
        }
        val model = modelsRepository.findByIdIncludingDeleted(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        modelsRepository.delete(model)
    }

    @GetMapping("/grouped")
    fun listModelsGrouped(): GroupedEntityResponse<ModelResponse> {
        val allModels = if (!CurrentUser.isAdmin()) {
            modelsRepository.findAccessibleForUser(
                userId = accessService.currentUserId(),
                ownerId = null,
                name = "",
                viewPermissions = viewPermissions,
                pageable = Pageable.unpaged()
            ).content
        } else {
            modelsRepository.findAll(Pageable.unpaged()).content
        }

        val groups = allModels
            .groupBy { it.name.trim().lowercase() }
            .map { (_, models) ->
                val sorted = models.sortedWith(compareModelsByVersionDesc)
                EntityGroupResponse(
                    name = sorted.first().name.trim(),
                    versions = sorted.map { it.toResponse() }
                )
            }
            .sortedBy { it.name.lowercase() }

        return GroupedEntityResponse(groups)
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

    @GetMapping("/{id}/related-versions")
    fun getRelatedVersions(@PathVariable id: UUID): List<ModelResponse> {
        val model = modelsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        accessService.requireCanViewModel(model)
        val byName = modelsRepository.findByNameAndDeletedFalse(model.name)
        val withSource = model.source?.let { listOf(it) } ?: emptyList()
        val derived = modelsRepository.findBySourceIdAndDeletedFalse(id)
        val combined = (byName + withSource + derived).distinctBy { it.id }
        val filtered = if (CurrentUser.isAdmin()) {
            combined
        } else {
            accessService.filterViewableModels(combined)
        }
        return filtered
            .sortedWith(compareModelsByVersionDesc)
            .map { it.toResponse() }
    }

    private val compareModelsByVersionDesc: Comparator<Models> = compareBy<Models> { parseSemver(it.version) == null }
        .thenByDescending { parseSemver(it.version)?.first ?: 0 }
        .thenByDescending { parseSemver(it.version)?.second ?: 0 }
        .thenByDescending { parseSemver(it.version)?.third ?: 0 }
        .thenByDescending { it.version }

    private fun parseSemver(version: String): Triple<Int, Int, Int>? {
        val parts = version.trim().split(".")
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        return Triple(major, minor, patch)
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
        // Конфликт только с неудалёнными: версия, занятая удалённой моделью, допустима
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
                stableId = sourceRoot.stableId,
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
                        stableId = srcNode.stableId,
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
        val linkIdMap = mutableMapOf<UUID, UUID>()
        for (srcLink in sourceLinks) {
            val newSourceId = nodeIdMap[srcLink.source.id!!]
                ?: continue
            val newTargetId = nodeIdMap[srcLink.target.id!!]
                ?: continue
            val newSource = nodesRepository.findById(newSourceId).orElseThrow { IllegalStateException("New source node not found") }
            val newTarget = nodesRepository.findById(newTargetId).orElseThrow { IllegalStateException("New target node not found") }
            val copiedLink = linksRepository.save(
                Links(
                    stableId = srcLink.stableId,
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
            linkIdMap[srcLink.id!!] = copiedLink.id!!
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
            val remappedAttrs = remapDiagramAttrs(srcDiagram.attrs, nodeIdMap, linkIdMap)
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

    private fun remapDiagramAttrs(
        attrs: String?,
        nodeIdMap: Map<UUID, UUID>,
        linkIdMap: Map<UUID, UUID>
    ): String? {
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
            val edges = instances.get("edges")
            if (edges != null && edges.isArray) {
                for (i in 0 until edges.size()) {
                    val edge = edges.get(i) ?: continue
                    val modelLinkId = edge.get("modelLinkId")?.asText() ?: continue
                    val oldUuid = try {
                        UUID.fromString(modelLinkId)
                    } catch (_: Exception) {
                        continue
                    }
                    val newUuid = linkIdMap[oldUuid] ?: continue
                    (edge as? ObjectNode)?.put("modelLinkId", newUuid.toString())
                }
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
            val hasSharedFromOwner = modelsRepository.existsAccessibleByOwnerForUser(
                ownerId = ownerId,
                userId = currentUserId,
                viewPermissions = viewPermissions
            )
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

data class GroupedEntityResponse<T>(val groups: List<EntityGroupResponse<T>>)
data class EntityGroupResponse<T>(val name: String, val versions: List<T>)
