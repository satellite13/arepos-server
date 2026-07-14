package ru.kavader.arepos.service.modelbatch

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.*
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.DiagramCanvasInstancesCleanupService
import java.time.Instant
import java.util.*

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
    private val typeUsageAuthorization: TypeUsageAuthorization,
    private val diagramAttrsRemapper: DiagramAttrsRemapper
) {
    fun execute(request: BatchSaveRequest, model: Models, owner: Users, now: Instant): BatchGraphExecutionResult {
        val nodeIdMap = mutableMapOf<String, UUID>()
        val linkIdMap = mutableMapOf<String, UUID>()
        val diagramIdMap = mutableMapOf<String, UUID>()

        val nodesUpdated = updateNodes(request.nodes.update, model, now)
        val nodesCreated = createNodesTopological(request.nodes.create, model, owner, now, nodeIdMap)
        val nodesDeleted = deleteModelScoped(
            entries = request.nodes.delete,
            model = model,
            kind = "Node",
            load = ::findNodeOrThrow,
            modelIdOf = { it.model.id },
            delete = nodesRepository::deleteById
        )

        val linksDeleted = deleteModelScoped(
            entries = request.links.delete,
            model = model,
            kind = "Link",
            load = ::findLinkOrThrow,
            modelIdOf = { it.model.id },
            delete = linksRepository::deleteById
        )
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
            typeUsageAuthorization.requireCanUseNodeTypeForModel(nodeType, model)
            val parentNode = resolveParentNode(upd.parentNodeId, emptyMap())
            node.name = upd.name
            node.nodeType = nodeType
            node.parentNode = parentNode
            node.attrs = upd.attrs
            node.updatedAt = now
            nodesRepository.save(node)
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
                        } catch (_: IllegalArgumentException) {
                            continue
                        }
                        findNodeOrThrow(parentUuid, "Parent node $parentRef not found")
                    }
                }
                val nodeType = findNodeTypeOrThrow(item.nodeTypeId)
                typeUsageAuthorization.requireCanUseNodeTypeForModel(nodeType, model)
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

    private fun <T> deleteModelScoped(
        entries: List<BatchDeleteEntry>,
        model: Models,
        kind: String,
        load: (UUID) -> T,
        modelIdOf: (T) -> UUID?,
        delete: (UUID) -> Unit
    ): Int {
        if (entries.isEmpty()) return 0
        val modelId = requireNotNull(model.id) { "Model id required" }
        for (entry in entries) {
            val id = entry.id
            val entity = load(id)
            if (modelIdOf(entity) != modelId) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "$kind $id does not belong to model $modelId"
                )
            }
            delete(id)
        }
        return entries.size
    }

    private fun createLinks(
        creates: List<BatchLinkCreate>, model: Models, owner: Users, now: Instant,
        nodeIdMap: Map<String, UUID>, linkIdMap: MutableMap<String, UUID>
    ): Int {
        for (item in creates) {
            val endpoints = resolveLinkEndpoints(item.sourceId, item.targetId, nodeIdMap)
            val linkType = authorizedLinkType(item.linkTypeId, model)
            val saved = linksRepository.save(
                Links(
                    stableId = resolveStableId(item.tempId),
                    source = endpoints.source,
                    target = endpoints.target,
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
            applyLinkFields(
                link = link,
                endpoints = resolveLinkEndpoints(upd.sourceId, upd.targetId, nodeIdMap),
                linkTypeId = upd.linkTypeId,
                attrs = upd.attrs,
                model = model,
                now = now
            )
            linksRepository.save(link)
        }
        return updates.size
    }

    private fun createDiagrams(
        creates: List<BatchDiagramCreate>, model: Models, owner: Users, now: Instant,
        nodeIdMap: Map<String, UUID>, linkIdMap: Map<String, UUID>,
        diagramIdMap: MutableMap<String, UUID>
    ): Int {
        for (item in creates) {
            val fields = prepareDiagramFields(
                notationId = item.notationId,
                nodeId = item.nodeId,
                attrs = item.attrs,
                model = model,
                nodeIdMap = nodeIdMap,
                linkIdMap = linkIdMap
            )
            val saved = diagramsRepository.save(
                Diagrams(
                    name = item.name,
                    version = item.version,
                    createdAt = now,
                    updatedAt = now,
                    attrs = fields.attrs,
                    owner = owner,
                    deleted = false,
                    model = model,
                    notation = fields.notation,
                    node = fields.node
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
            val fields = prepareDiagramFields(
                notationId = upd.notationId,
                nodeId = upd.nodeId,
                attrs = upd.attrs,
                model = model,
                nodeIdMap = nodeIdMap,
                linkIdMap = linkIdMap
            )
            diagram.name = upd.name
            diagram.version = upd.version
            diagram.notation = fields.notation
            diagram.node = fields.node
            diagram.attrs = fields.attrs
            diagram.updatedAt = now
            diagramsRepository.save(diagram)
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
        } catch (_: IllegalArgumentException) {
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

    private data class LinkEndpoints(val source: Nodes, val target: Nodes)

    private fun resolveLinkEndpoints(
        sourceRef: String,
        targetRef: String,
        nodeIdMap: Map<String, UUID>
    ): LinkEndpoints {
        val sourceId = resolveRef(sourceRef, nodeIdMap, "source node")
        val targetId = resolveRef(targetRef, nodeIdMap, "target node")
        return LinkEndpoints(
            source = findNodeOrThrow(sourceId, "Source node $sourceRef not found"),
            target = findNodeOrThrow(targetId, "Target node $targetRef not found")
        )
    }

    private fun authorizedLinkType(linkTypeId: UUID, model: Models): LinkTypes {
        val linkType = findLinkTypeOrThrow(linkTypeId)
        typeUsageAuthorization.requireCanUseLinkTypeForModel(linkType, model)
        return linkType
    }

    private fun applyLinkFields(
        link: Links,
        endpoints: LinkEndpoints,
        linkTypeId: UUID,
        attrs: String?,
        model: Models,
        now: Instant
    ) {
        val linkType = authorizedLinkType(linkTypeId, model)
        link.source = endpoints.source
        link.target = endpoints.target
        link.linkType = linkType
        link.attrs = attrs
        link.updatedAt = now
    }

    private data class PreparedDiagramFields(
        val notation: Notations,
        val node: Nodes?,
        val attrs: String?
    )

    private fun prepareDiagramFields(
        notationId: UUID,
        nodeId: String?,
        attrs: String?,
        model: Models,
        nodeIdMap: Map<String, UUID>,
        linkIdMap: Map<String, UUID>
    ): PreparedDiagramFields {
        val notation = findNotationOrThrow(notationId)
        accessService.requireCanReferenceNotationForModelDiagram(notation, model)
        val node = nodeId?.let { ref ->
            val nodeUuid = resolveRef(ref, nodeIdMap, "diagram node")
            findNodeOrThrow(nodeUuid, "Node $ref not found")
        }
        return PreparedDiagramFields(
            notation = notation,
            node = node,
            attrs = diagramAttrsRemapper.remap(attrs, nodeIdMap, linkIdMap)
        )
    }

    private fun <T> findOrThrow(
        id: UUID,
        loader: (UUID) -> Optional<T>,
        kind: String,
        message: String? = null,
        status: HttpStatus = HttpStatus.NOT_FOUND
    ): T = loader(id).orElseThrow {
        ResponseStatusException(status, message ?: "$kind $id not found")
    }

    private fun findNodeOrThrow(
        id: UUID,
        message: String? = null,
        status: HttpStatus = HttpStatus.NOT_FOUND
    ): Nodes = findOrThrow(id, nodesRepository::findById, "Node", message, status)

    private fun findLinkOrThrow(id: UUID, message: String? = null): Links =
        findOrThrow(id, linksRepository::findById, "Link", message)

    private fun findDiagramOrThrow(id: UUID, message: String? = null): Diagrams =
        findOrThrow(id, diagramsRepository::findById, "Diagram", message)

    private fun findNodeTypeOrThrow(id: UUID, message: String? = null): NodeTypes =
        findOrThrow(id, nodeTypesRepository::findById, "NodeType", message)

    private fun findLinkTypeOrThrow(id: UUID, message: String? = null): LinkTypes =
        findOrThrow(id, linkTypesRepository::findById, "LinkType", message)

    private fun findNotationOrThrow(id: UUID, message: String? = null): Notations =
        findOrThrow(id, notationsRepository::findById, "Notation", message)

}
