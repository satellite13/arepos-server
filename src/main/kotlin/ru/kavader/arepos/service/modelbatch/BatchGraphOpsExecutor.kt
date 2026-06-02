package ru.kavader.arepos.service.modelbatch

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.BatchDeleteEntry
import ru.kavader.arepos.dto.model.BatchDiagramCreate
import ru.kavader.arepos.dto.model.BatchDiagramUpdate
import ru.kavader.arepos.dto.model.BatchLinkCreate
import ru.kavader.arepos.dto.model.BatchLinkUpdate
import ru.kavader.arepos.dto.model.BatchNodeCreate
import ru.kavader.arepos.dto.model.BatchNodeUpdate
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.DiagramCanvasInstancesCleanupService
import ru.kavader.arepos.service.ModelDiagramTypeValidator
import java.time.Instant
import java.util.UUID

data class BatchGraphExecutionResult(
    val nodeIdMap: Map<String, UUID>,
    val linkIdMap: Map<String, UUID>,
    val diagramIdMap: Map<String, UUID>,
    val nodesCreated: Int,
    val nodesUpdated: Int,
    val nodesDeleted: Int,
    val linksCreated: Int,
    val linksUpdated: Int,
    val linksDeleted: Int,
    val diagramsCreated: Int,
    val diagramsUpdated: Int,
    val diagramsDeleted: Int
) {
    val mutated: Boolean = nodesCreated > 0 ||
        nodesUpdated > 0 ||
        nodesDeleted > 0 ||
        linksCreated > 0 ||
        linksUpdated > 0 ||
        linksDeleted > 0 ||
        diagramsCreated > 0 ||
        diagramsUpdated > 0 ||
        diagramsDeleted > 0
}

@Component
class BatchGraphOpsExecutor(
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val notationsRepository: NotationsRepository,
    private val accessService: ResourceAccessService,
    private val diagramCanvasInstancesCleanupService: DiagramCanvasInstancesCleanupService,
    private val typeValidator: ModelDiagramTypeValidator,
    private val diagramAttrsRemapper: DiagramAttrsRemapper
) {
    fun execute(request: BatchSaveRequest, model: Models, owner: Users, now: Instant): BatchGraphExecutionResult {
        val nodeIdMap = mutableMapOf<String, UUID>()
        val linkIdMap = mutableMapOf<String, UUID>()
        val diagramIdMap = mutableMapOf<String, UUID>()

        val nodesUpdated = updateNodes(request.nodes.update, model, now)
        val nodesCreated = createNodesTopological(request.nodes.create, model, owner, now, nodeIdMap)
        val nodesDeleted = deleteNodes(request.nodes.delete, model)

        val linksDeleted = deleteLinks(request.links.delete, model)
        val linksCreated = createLinks(request.links.create, model, owner, now, nodeIdMap, linkIdMap)
        val linksUpdated = updateLinks(request.links.update, model, now, nodeIdMap)

        val diagramsDeleted = deleteDiagrams(request.diagrams.delete)
        val diagramsCreated = createDiagrams(
            request.diagrams.create, model, owner, now, nodeIdMap, linkIdMap, diagramIdMap
        )
        val diagramsUpdated = updateDiagrams(request.diagrams.update, model, now, nodeIdMap, linkIdMap)

        val deletedNodeIds = request.nodes.delete.map { it.id }
        val deletedLinkIds = request.links.delete.map { it.id }
        if (deletedNodeIds.isNotEmpty() || deletedLinkIds.isNotEmpty()) {
            diagramCanvasInstancesCleanupService.removeDeletedModelEntitiesFromAllDiagrams(
                requireNotNull(model.id),
                deletedNodeIds,
                deletedLinkIds,
                now
            )
        }

        return BatchGraphExecutionResult(
            nodeIdMap = nodeIdMap,
            linkIdMap = linkIdMap,
            diagramIdMap = diagramIdMap,
            nodesCreated = nodesCreated,
            nodesUpdated = nodesUpdated,
            nodesDeleted = nodesDeleted,
            linksCreated = linksCreated,
            linksUpdated = linksUpdated,
            linksDeleted = linksDeleted,
            diagramsCreated = diagramsCreated,
            diagramsUpdated = diagramsUpdated,
            diagramsDeleted = diagramsDeleted
        )
    }

    private fun updateNodes(updates: List<BatchNodeUpdate>, model: Models, now: Instant): Int {
        for (upd in updates) {
            val node = findNodeOrThrow(upd.id)
            val nodeType = findNodeTypeOrThrow(upd.nodeTypeId)
            requireCanUseNodeTypeForModel(nodeType, model)
            val parentNode = resolveParentNode(upd.parentNodeId, emptyMap())
            nodesRepository.save(
                node.copy(
                    name = upd.name,
                    nodeType = nodeType,
                    parentNode = parentNode,
                    attrs = upd.attrs,
                    updatedAt = now
                )
            )
        }
        return updates.size
    }

    private fun createNodesTopological(
        creates: List<BatchNodeCreate>, model: Models, owner: Users, now: Instant,
        nodeIdMap: MutableMap<String, UUID>
    ): Int {
        val pending = creates.toMutableList()
        var created = 0
        while (pending.isNotEmpty()) {
            var progress = false
            val iter = pending.iterator()
            while (iter.hasNext()) {
                val item = iter.next()
                val parentRef = item.parentNodeId
                val parentNode: Nodes? = when {
                    parentRef == null -> null
                    nodeIdMap.containsKey(parentRef) -> {
                        findNodeOrThrow(
                            requireNotNull(nodeIdMap[parentRef]),
                            "Just-created node ${nodeIdMap[parentRef]} not found",
                            HttpStatus.INTERNAL_SERVER_ERROR
                        )
                    }
                    else -> {
                        val parentUuid = try {
                            UUID.fromString(parentRef)
                        } catch (_: Exception) {
                            continue
                        }
                        findNodeOrThrow(parentUuid, "Parent node $parentRef not found")
                    }
                }
                val nodeType = findNodeTypeOrThrow(item.nodeTypeId)
                requireCanUseNodeTypeForModel(nodeType, model)
                val saved = nodesRepository.save(
                    Nodes(
                        stableId = resolveStableId(item.tempId),
                        name = item.name,
                        model = model,
                        owner = owner,
                        nodeType = nodeType,
                        parentNode = parentNode,
                        attrs = item.attrs,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                nodeIdMap[item.tempId] = saved.id!!
                iter.remove()
                progress = true
                created++
            }
            if (!progress) {
                val unresolvedIds = pending.map { it.tempId }
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Circular or unresolvable parent references in node creates: $unresolvedIds"
                )
            }
        }
        return created
    }

    private fun deleteNodes(entries: List<BatchDeleteEntry>, model: Models): Int {
        if (entries.isEmpty()) return 0
        val modelId = requireNotNull(model.id) { "Model id required" }
        for (entry in entries) {
            val id = entry.id
            val node = findNodeOrThrow(id)
            if (node.model.id != modelId) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Node $id does not belong to model $modelId")
            }
            nodesRepository.deleteById(id)
        }
        return entries.size
    }

    private fun createLinks(
        creates: List<BatchLinkCreate>, model: Models, owner: Users, now: Instant,
        nodeIdMap: Map<String, UUID>, linkIdMap: MutableMap<String, UUID>
    ): Int {
        for (item in creates) {
            val sourceId = resolveRef(item.sourceId, nodeIdMap, "source node")
            val targetId = resolveRef(item.targetId, nodeIdMap, "target node")
            val source = findNodeOrThrow(sourceId, "Source node ${item.sourceId} not found")
            val target = findNodeOrThrow(targetId, "Target node ${item.targetId} not found")
            val linkType = findLinkTypeOrThrow(item.linkTypeId)
            requireCanUseLinkTypeForModel(linkType, model)
            val saved = linksRepository.save(
                Links(
                    stableId = resolveStableId(item.tempId),
                    source = source,
                    target = target,
                    attrs = item.attrs,
                    createdAt = now,
                    updatedAt = now,
                    owner = owner,
                    linkType = linkType,
                    model = model
                )
            )
            linkIdMap[item.tempId] = saved.id!!
        }
        return creates.size
    }

    private fun updateLinks(
        updates: List<BatchLinkUpdate>, model: Models, now: Instant,
        nodeIdMap: Map<String, UUID>
    ): Int {
        for (upd in updates) {
            val link = findLinkOrThrow(upd.id)
            val sourceId = resolveRef(upd.sourceId, nodeIdMap, "source node")
            val targetId = resolveRef(upd.targetId, nodeIdMap, "target node")
            val source = findNodeOrThrow(sourceId, "Source node ${upd.sourceId} not found")
            val target = findNodeOrThrow(targetId, "Target node ${upd.targetId} not found")
            val linkType = findLinkTypeOrThrow(upd.linkTypeId)
            requireCanUseLinkTypeForModel(linkType, model)
            linksRepository.save(
                link.copy(
                    source = source,
                    target = target,
                    linkType = linkType,
                    attrs = upd.attrs,
                    updatedAt = now
                )
            )
        }
        return updates.size
    }

    private fun deleteLinks(entries: List<BatchDeleteEntry>, model: Models): Int {
        if (entries.isEmpty()) return 0
        val modelId = requireNotNull(model.id) { "Model id required" }
        for (entry in entries) {
            val id = entry.id
            val link = findLinkOrThrow(id)
            if (link.model.id != modelId) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Link $id does not belong to model $modelId")
            }
            linksRepository.deleteById(id)
        }
        return entries.size
    }

    private fun createDiagrams(
        creates: List<BatchDiagramCreate>, model: Models, owner: Users, now: Instant,
        nodeIdMap: Map<String, UUID>, linkIdMap: Map<String, UUID>,
        diagramIdMap: MutableMap<String, UUID>
    ): Int {
        for (item in creates) {
            val notation = findNotationOrThrow(item.notationId)
            accessService.requireCanReferenceNotationForModelDiagram(notation, model)
            val node = item.nodeId?.let { ref ->
                val nodeUuid = resolveRef(ref, nodeIdMap, "diagram node")
                findNodeOrThrow(nodeUuid, "Node $ref not found")
            }
            val remappedAttrs = diagramAttrsRemapper.remap(item.attrs, nodeIdMap, linkIdMap)
            val saved = diagramsRepository.save(
                Diagrams(
                    name = item.name,
                    version = item.version,
                    createdAt = now,
                    updatedAt = now,
                    attrs = remappedAttrs,
                    owner = owner,
                    deleted = false,
                    model = model,
                    notation = notation,
                    node = node
                )
            )
            diagramIdMap[item.tempId] = saved.id!!
        }
        return creates.size
    }

    private fun updateDiagrams(
        updates: List<BatchDiagramUpdate>, model: Models, now: Instant,
        nodeIdMap: Map<String, UUID>, linkIdMap: Map<String, UUID>
    ): Int {
        for (upd in updates) {
            val diagram = findDiagramOrThrow(upd.id)
            val notation = findNotationOrThrow(upd.notationId)
            accessService.requireCanReferenceNotationForModelDiagram(notation, model)
            val node = upd.nodeId?.let { ref ->
                val nodeUuid = resolveRef(ref, nodeIdMap, "diagram node")
                findNodeOrThrow(nodeUuid, "Node $ref not found")
            }
            val remappedAttrs = diagramAttrsRemapper.remap(upd.attrs, nodeIdMap, linkIdMap)
            diagramsRepository.save(
                diagram.copy(
                    name = upd.name,
                    version = upd.version,
                    notation = notation,
                    node = node,
                    attrs = remappedAttrs,
                    updatedAt = now
                )
            )
        }
        return updates.size
    }

    private fun deleteDiagrams(entries: List<BatchDeleteEntry>): Int {
        for (entry in entries) {
            val id = entry.id
            if (!diagramsRepository.existsById(id)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }
            diagramsRepository.softDeleteById(id)
        }
        return entries.size
    }

    private fun resolveRef(ref: String, idMap: Map<String, UUID>, label: String): UUID {
        idMap[ref]?.let { return it }
        return try {
            UUID.fromString(ref)
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot resolve $label reference: $ref")
        }
    }

    private fun resolveStableId(tempId: String): UUID =
        try {
            UUID.fromString(tempId)
        } catch (_: IllegalArgumentException) {
            UUID.randomUUID()
        }

    private fun resolveParentNode(ref: String?, nodeIdMap: Map<String, UUID>): Nodes? {
        if (ref == null) return null
        val uuid = resolveRef(ref, nodeIdMap, "parent node")
        return findNodeOrThrow(uuid, "Parent node $ref not found")
    }

    private fun findNodeOrThrow(
        id: UUID,
        message: String = "Node $id not found",
        status: HttpStatus = HttpStatus.NOT_FOUND
    ): Nodes = nodesRepository.findById(id).orElseThrow { ResponseStatusException(status, message) }

    private fun findLinkOrThrow(id: UUID, message: String = "Link $id not found"): Links =
        linksRepository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, message) }

    private fun findDiagramOrThrow(id: UUID, message: String = "Diagram $id not found"): Diagrams =
        diagramsRepository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, message) }

    private fun findNodeTypeOrThrow(id: UUID, message: String = "NodeType $id not found"): NodeTypes =
        nodeTypesRepository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, message) }

    private fun findLinkTypeOrThrow(id: UUID, message: String = "LinkType $id not found"): LinkTypes =
        linkTypesRepository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, message) }

    private fun findNotationOrThrow(id: UUID, message: String = "Notation $id not found"): Notations =
        notationsRepository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, message) }

    private fun requireCanUseNodeTypeForModel(nodeType: NodeTypes, model: Models) {
        if (accessService.canUseNodeType(nodeType)) return
        val canEditModel = accessService.canEditModel(model)
        if (canEditModel && nodeType.owner.id == model.owner.id) return
        if (canEditModel && isNodeTypeUsedInModelDiagramNotations(requireNotNull(nodeType.id), model)) return
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
    }

    private fun requireCanUseLinkTypeForModel(linkType: LinkTypes, model: Models) {
        if (accessService.canUseLinkType(linkType)) return
        val canEditModel = accessService.canEditModel(model)
        if (canEditModel && linkType.owner.id == model.owner.id) return
        if (canEditModel && isLinkTypeUsedInModelDiagramNotations(requireNotNull(linkType.id), model)) return
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
    }

    private fun isNodeTypeUsedInModelDiagramNotations(nodeTypeId: UUID, model: Models): Boolean =
        typeValidator.isNodeTypeUsedInModelDiagramNotations(nodeTypeId, requireNotNull(model.id))

    private fun isLinkTypeUsedInModelDiagramNotations(linkTypeId: UUID, model: Models): Boolean =
        typeValidator.isLinkTypeUsedInModelDiagramNotations(linkTypeId, requireNotNull(model.id))
}
