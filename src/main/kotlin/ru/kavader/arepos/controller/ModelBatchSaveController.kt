package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.BatchConflictItem
import ru.kavader.arepos.dto.BatchDeleteEntry
import ru.kavader.arepos.dto.BatchDiagramCreate
import ru.kavader.arepos.dto.BatchDiagramUpdate
import ru.kavader.arepos.dto.BatchLinkCreate
import ru.kavader.arepos.dto.BatchLinkUpdate
import ru.kavader.arepos.dto.BatchNodeCreate
import ru.kavader.arepos.dto.BatchNodeUpdate
import ru.kavader.arepos.dto.BatchSaveConflictException
import ru.kavader.arepos.dto.BatchSaveRequest
import ru.kavader.arepos.dto.BatchSaveResponse
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.DiagramCanvasInstancesCleanupService
import ru.kavader.arepos.service.ModelSyncBroadcaster
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
class ModelBatchSaveController(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val objectMapper: ObjectMapper,
    private val diagramCanvasInstancesCleanupService: DiagramCanvasInstancesCleanupService,
    private val modelSyncBroadcaster: ModelSyncBroadcaster
) {

    @PostMapping("/{modelId}/batch-save")
    @Transactional
    fun batchSave(
        @PathVariable modelId: UUID,
        @RequestBody request: BatchSaveRequest
    ): BatchSaveResponse {
        val model = modelsRepository.findById(modelId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found") }
        accessService.requireCanEditModel(model)

        val conflicts = collectBatchConflicts(request, model)
        if (conflicts.isNotEmpty()) {
            throw BatchSaveConflictException(conflicts)
        }

        val owner = usersRepository.findById(accessService.currentUserId())
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found") }
        val now = Instant.now()

        val nodeIdMap = mutableMapOf<String, UUID>()
        val linkIdMap = mutableMapOf<String, UUID>()
        val diagramIdMap = mutableMapOf<String, UUID>()

        // --- Nodes ---
        val nodesUpdated = updateNodes(request.nodes.update, model, owner, now)
        val nodesCreated = createNodesTopological(request.nodes.create, model, owner, now, nodeIdMap)
        val nodesDeleted = deleteNodes(request.nodes.delete, model)

        // --- Links ---
        val linksDeleted = deleteLinks(request.links.delete, model)
        val linksCreated = createLinks(request.links.create, model, owner, now, nodeIdMap, linkIdMap)
        val linksUpdated = updateLinks(request.links.update, model, owner, now, nodeIdMap)

        // --- Diagrams ---
        val diagramsDeleted = deleteDiagrams(request.diagrams.delete)
        val diagramsCreated = createDiagrams(
            request.diagrams.create, model, owner, now, nodeIdMap, linkIdMap, diagramIdMap
        )
        val diagramsUpdated = updateDiagrams(
            request.diagrams.update, model, owner, now, nodeIdMap, linkIdMap
        )

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

        val mutated = request.nodes.create.isNotEmpty() ||
            request.nodes.update.isNotEmpty() ||
            request.nodes.delete.isNotEmpty() ||
            request.links.create.isNotEmpty() ||
            request.links.update.isNotEmpty() ||
            request.links.delete.isNotEmpty() ||
            request.diagrams.create.isNotEmpty() ||
            request.diagrams.update.isNotEmpty() ||
            request.diagrams.delete.isNotEmpty()
        if (mutated) {
            modelSyncBroadcaster.broadcastModelChanged(requireNotNull(model.id), "batch_save")
        }

        return BatchSaveResponse(
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

    // ── Node operations ─────────────────────────────────────────────

    private fun updateNodes(
        updates: List<BatchNodeUpdate>,
        model: Models,
        owner: Users,
        now: Instant
    ): Int {
        for (upd in updates) {
            val node = nodesRepository.findById(upd.id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Node ${upd.id} not found") }
            val nodeType = nodeTypesRepository.findById(upd.nodeTypeId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType ${upd.nodeTypeId} not found") }
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
        creates: List<BatchNodeCreate>,
        model: Models,
        owner: Users,
        now: Instant,
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
                        nodesRepository.findById(nodeIdMap[parentRef]!!).orElseThrow {
                            ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Just-created node ${nodeIdMap[parentRef]} not found"
                            )
                        }
                    }
                    else -> {
                        val parentUuid = try { UUID.fromString(parentRef) } catch (_: Exception) { continue }
                        nodesRepository.findById(parentUuid).orElseThrow {
                            ResponseStatusException(HttpStatus.NOT_FOUND, "Parent node $parentRef not found")
                        }
                    }
                }
                val nodeType = nodeTypesRepository.findById(item.nodeTypeId)
                    .orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType ${item.nodeTypeId} not found")
                    }
                requireCanUseNodeTypeForModel(nodeType, model)
                val saved = nodesRepository.save(
                    Nodes(
                        stableId = UUID.randomUUID(),
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
            val node = nodesRepository.findById(id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found") }
            if (node.model.id != modelId) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Node $id does not belong to model $modelId"
                )
            }
            nodesRepository.deleteById(id)
        }
        return entries.size
    }

    // ── Link operations ─────────────────────────────────────────────

    private fun createLinks(
        creates: List<BatchLinkCreate>,
        model: Models,
        owner: Users,
        now: Instant,
        nodeIdMap: Map<String, UUID>,
        linkIdMap: MutableMap<String, UUID>
    ): Int {
        for (item in creates) {
            val sourceId = resolveRef(item.sourceId, nodeIdMap, "source node")
            val targetId = resolveRef(item.targetId, nodeIdMap, "target node")
            val source = nodesRepository.findById(sourceId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Source node ${item.sourceId} not found") }
            val target = nodesRepository.findById(targetId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Target node ${item.targetId} not found") }
            val linkType = linkTypesRepository.findById(item.linkTypeId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType ${item.linkTypeId} not found") }
            requireCanUseLinkTypeForModel(linkType, model)
            val saved = linksRepository.save(
                Links(
                    stableId = UUID.randomUUID(),
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
        updates: List<BatchLinkUpdate>,
        model: Models,
        owner: Users,
        now: Instant,
        nodeIdMap: Map<String, UUID>
    ): Int {
        for (upd in updates) {
            val link = linksRepository.findById(upd.id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Link ${upd.id} not found") }
            val sourceId = resolveRef(upd.sourceId, nodeIdMap, "source node")
            val targetId = resolveRef(upd.targetId, nodeIdMap, "target node")
            val source = nodesRepository.findById(sourceId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Source node ${upd.sourceId} not found") }
            val target = nodesRepository.findById(targetId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Target node ${upd.targetId} not found") }
            val linkType = linkTypesRepository.findById(upd.linkTypeId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType ${upd.linkTypeId} not found") }
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
            val link = linksRepository.findById(id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found") }
            if (link.model.id != modelId) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Link $id does not belong to model $modelId"
                )
            }
            linksRepository.deleteById(id)
        }
        return entries.size
    }

    // ── Diagram operations ──────────────────────────────────────────

    private fun createDiagrams(
        creates: List<BatchDiagramCreate>,
        model: Models,
        owner: Users,
        now: Instant,
        nodeIdMap: Map<String, UUID>,
        linkIdMap: Map<String, UUID>,
        diagramIdMap: MutableMap<String, UUID>
    ): Int {
        for (item in creates) {
            val notation = notationsRepository.findById(item.notationId)
                .orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${item.notationId} not found")
                }
            accessService.requireCanViewNotation(notation)
            val node = item.nodeId?.let { ref ->
                val nodeUuid = resolveRef(ref, nodeIdMap, "diagram node")
                nodesRepository.findById(nodeUuid)
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Node $ref not found") }
            }
            val remappedAttrs = remapDiagramAttrs(item.attrs, nodeIdMap, linkIdMap)
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
        updates: List<BatchDiagramUpdate>,
        model: Models,
        owner: Users,
        now: Instant,
        nodeIdMap: Map<String, UUID>,
        linkIdMap: Map<String, UUID>
    ): Int {
        for (upd in updates) {
            val diagram = diagramsRepository.findById(upd.id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram ${upd.id} not found") }
            val notation = notationsRepository.findById(upd.notationId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${upd.notationId} not found") }
            accessService.requireCanViewNotation(notation)
            val node = upd.nodeId?.let { ref ->
                val nodeUuid = resolveRef(ref, nodeIdMap, "diagram node")
                nodesRepository.findById(nodeUuid)
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Node $ref not found") }
            }
            val remappedAttrs = remapDiagramAttrs(upd.attrs, nodeIdMap, linkIdMap)
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

    // ── Helpers ──────────────────────────────────────────────────────

    private fun isVersionConflict(server: Instant?, clientBase: Instant): Boolean {
        if (server == null) return true
        return server.toEpochMilli() != clientBase.toEpochMilli()
    }

    private fun collectBatchConflicts(request: BatchSaveRequest, model: Models): List<BatchConflictItem> {
        if (request.force) return emptyList()
        val modelId = requireNotNull(model.id) { "Model id required" }
        val conflicts = mutableListOf<BatchConflictItem>()

        for (upd in request.nodes.update) {
            val base = upd.baseUpdatedAt ?: continue
            val node = nodesRepository.findById(upd.id).orElse(null) ?: continue
            if (node.model.id != modelId) continue
            if (isVersionConflict(node.updatedAt, base)) {
                conflicts.add(BatchConflictItem("node", upd.id, node.updatedAt, base))
            }
        }
        for (del in request.nodes.delete) {
            val base = del.baseUpdatedAt ?: continue
            val node = nodesRepository.findById(del.id).orElse(null) ?: continue
            if (node.model.id != modelId) continue
            if (isVersionConflict(node.updatedAt, base)) {
                conflicts.add(BatchConflictItem("node", del.id, node.updatedAt, base))
            }
        }

        for (upd in request.links.update) {
            val base = upd.baseUpdatedAt ?: continue
            val link = linksRepository.findById(upd.id).orElse(null) ?: continue
            if (link.model.id != modelId) continue
            if (isVersionConflict(link.updatedAt, base)) {
                conflicts.add(BatchConflictItem("link", upd.id, link.updatedAt, base))
            }
        }
        for (del in request.links.delete) {
            val base = del.baseUpdatedAt ?: continue
            val link = linksRepository.findById(del.id).orElse(null) ?: continue
            if (link.model.id != modelId) continue
            if (isVersionConflict(link.updatedAt, base)) {
                conflicts.add(BatchConflictItem("link", del.id, link.updatedAt, base))
            }
        }

        for (upd in request.diagrams.update) {
            val base = upd.baseUpdatedAt ?: continue
            val diagram = diagramsRepository.findById(upd.id).orElse(null) ?: continue
            if (diagram.model.id != modelId) continue
            if (isVersionConflict(diagram.updatedAt, base)) {
                conflicts.add(BatchConflictItem("diagram", upd.id, diagram.updatedAt, base))
            }
        }
        for (del in request.diagrams.delete) {
            val base = del.baseUpdatedAt ?: continue
            val diagram = diagramsRepository.findById(del.id).orElse(null) ?: continue
            if (diagram.model.id != modelId) continue
            if (isVersionConflict(diagram.updatedAt, base)) {
                conflicts.add(BatchConflictItem("diagram", del.id, diagram.updatedAt, base))
            }
        }

        return conflicts
    }

    private fun resolveRef(ref: String, idMap: Map<String, UUID>, label: String): UUID {
        idMap[ref]?.let { return it }
        return try {
            UUID.fromString(ref)
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot resolve $label reference: $ref")
        }
    }

    private fun resolveParentNode(ref: String?, nodeIdMap: Map<String, UUID>): Nodes? {
        if (ref == null) return null
        val uuid = resolveRef(ref, nodeIdMap, "parent node")
        return nodesRepository.findById(uuid).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Parent node $ref not found")
        }
    }

    private fun remapDiagramAttrs(
        attrs: String?,
        nodeIdMap: Map<String, UUID>,
        linkIdMap: Map<String, UUID>
    ): String? {
        if (attrs == null) return null
        if (nodeIdMap.isEmpty() && linkIdMap.isEmpty()) return attrs

        val root = try {
            objectMapper.readTree(attrs)
        } catch (_: Exception) {
            return attrs
        }
        if (!root.isObject) return attrs

        var changed = false
        val rootObj = root as ObjectNode

        // warchi: { "instances": { "nodes": [...], "edges": [...] } }
        val instances = rootObj.get("instances")
        if (instances != null && instances.isObject) {
            val instObj = instances as ObjectNode
            val inNodes = instObj.get("nodes")
            if (inNodes != null && inNodes.isArray) {
                for (element in inNodes) {
                    if (element is ObjectNode) {
                        changed = remapField(element, "modelNodeId", nodeIdMap) || changed
                    }
                }
            }
            val inEdges = instObj.get("edges")
            if (inEdges != null && inEdges.isArray) {
                for (element in inEdges) {
                    if (element is ObjectNode) {
                        changed = remapField(element, "modelLinkId", linkIdMap) || changed
                        changed = remapField(element, "sourceModelNodeId", nodeIdMap) || changed
                        changed = remapField(element, "targetModelNodeId", nodeIdMap) || changed
                    }
                }
            }
        }

        // legacy / другие клиенты: nodes и edges на корне JSON
        val nodesArray = rootObj.get("nodes")
        if (nodesArray != null && nodesArray.isArray) {
            for (element in nodesArray) {
                if (element is ObjectNode) {
                    changed = remapField(element, "modelNodeId", nodeIdMap) || changed
                }
            }
        }

        val edgesArray = rootObj.get("edges")
        if (edgesArray != null && edgesArray.isArray) {
            for (element in edgesArray) {
                if (element is ObjectNode) {
                    changed = remapField(element, "modelLinkId", linkIdMap) || changed
                    changed = remapField(element, "sourceModelNodeId", nodeIdMap) || changed
                    changed = remapField(element, "targetModelNodeId", nodeIdMap) || changed
                }
            }
        }

        return if (changed) objectMapper.writeValueAsString(rootObj) else attrs
    }

    private fun remapField(obj: ObjectNode, field: String, idMap: Map<String, UUID>): Boolean {
        val value = obj.get(field)?.asText() ?: return false
        val mapped = idMap[value] ?: return false
        obj.put(field, mapped.toString())
        return true
    }

    private fun requireCanUseNodeTypeForModel(
        nodeType: NodeTypes,
        model: Models
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

    private fun requireCanUseLinkTypeForModel(
        linkType: LinkTypes,
        model: Models
    ) {
        if (accessService.canUseLinkType(linkType)) return
        val canEditModel = accessService.canEditModel(model)
        if (canEditModel && linkType.owner.id == model.owner.id) return
        if (
            canEditModel &&
                isLinkTypeUsedInModelDiagramNotations(requireNotNull(linkType.id), model)
        ) {
            return
        }
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
    }

    private fun isNodeTypeUsedInModelDiagramNotations(
        nodeTypeId: UUID,
        model: Models
    ): Boolean {
        val notationIds = diagramsRepository.findDistinctNotationIdsByModelId(requireNotNull(model.id)).toSet()
        if (notationIds.isEmpty()) return false
        return componentsRepository.existsByNodeType_IdAndNotation_IdIn(nodeTypeId, notationIds)
    }

    private fun isLinkTypeUsedInModelDiagramNotations(
        linkTypeId: UUID,
        model: Models
    ): Boolean {
        val notationIds = diagramsRepository.findDistinctNotationIdsByModelId(requireNotNull(model.id)).toSet()
        if (notationIds.isEmpty()) return false
        return relationsRepository.existsByLinkType_IdAndNotation_IdIn(linkTypeId, notationIds)
    }
}
