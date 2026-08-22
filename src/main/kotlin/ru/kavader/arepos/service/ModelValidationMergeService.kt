package ru.kavader.arepos.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramRef
import ru.kavader.arepos.dto.model.MergeLinksPreviewResponse
import ru.kavader.arepos.dto.model.MergeLinksRequest
import ru.kavader.arepos.dto.model.MergeLinksResponse
import ru.kavader.arepos.dto.model.MergeNodesPreviewResponse
import ru.kavader.arepos.dto.model.MergeNodesRequest
import ru.kavader.arepos.dto.model.MergeNodesResponse
import ru.kavader.arepos.dto.model.PreviewIncidentLink
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.DiagramEditLocksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.DIAGRAM_LOCK_HELD_BY_ANOTHER_USER
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ModelValidationMergeService(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val diagramEditLocksRepository: DiagramEditLocksRepository,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val objectMapper: ObjectMapper
) {
    fun previewNodes(modelId: UUID, keepId: UUID, dropId: UUID): MergeNodesPreviewResponse {
        requireViewableModel(modelId)
        if (keepId == dropId) {
            throw notADuplicatePair()
        }
        val nodes = nodesRepository.findByModel_IdAndIdIn(modelId, listOf(keepId, dropId))
            .associateBy { requireNotNull(it.id) }
        val keep = nodes[keepId] ?: throw nodeNotFound(keepId)
        val drop = nodes[dropId] ?: throw nodeNotFound(dropId)
        if (!isDuplicateNodePair(keep, drop)) {
            throw notADuplicatePair()
        }

        val keepIncidentLinks = linksRepository.findByModelIdAndEndpointNodeId(modelId, keep.id!!)
        val dropIncidentLinks = linksRepository.findByModelIdAndEndpointNodeId(modelId, drop.id!!)
        val uniqueLinks = mutableListOf<PreviewIncidentLink>()
        val linksToDelete = mutableListOf<PreviewIncidentLink>()
        for (link in dropIncidentLinks) {
            val preview = toPreviewIncidentLink(drop, link)
            when (classify(drop, keep, link, keepIncidentLinks)) {
                Kind.UNIQUE -> uniqueLinks.add(preview)
                Kind.MATCHING, Kind.AB -> linksToDelete.add(preview)
            }
        }

        return MergeNodesPreviewResponse(
            keepId = keep.id!!,
            dropId = drop.id!!,
            keepTypeProperties = parseTypeProperties(keep.attrs),
            dropTypeProperties = parseTypeProperties(drop.attrs),
            uniqueLinks = uniqueLinks,
            linksToDelete = linksToDelete,
            keepDiagrams = diagramsForNode(modelId, keep.id!!),
            dropDiagrams = diagramsForNode(modelId, drop.id!!),
            hasChildren = nodesRepository.existsByParentNode_Id(drop.id!!),
            hasDocuments = hasDocuments(drop.attrs),
            diagramsToReparentCount = diagramsRepository.countByNode_IdAndDeletedFalse(drop.id!!),
            keepUpdatedAt = entityTimestamp(keep.updatedAt, keep.createdAt),
            dropUpdatedAt = entityTimestamp(drop.updatedAt, drop.createdAt)
        )
    }

    @Transactional
    fun mergeNodes(modelId: UUID, request: MergeNodesRequest): MergeNodesResponse {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanEditModel(model)
        if (request.keepId == request.dropId) {
            throw notADuplicatePair()
        }
        val nodes = nodesRepository.findByModel_IdAndIdIn(modelId, listOf(request.keepId, request.dropId))
            .associateBy { requireNotNull(it.id) }
        val keep = nodes[request.keepId] ?: throw nodeNotFound(request.keepId)
        val drop = nodes[request.dropId] ?: throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "Node ${request.dropId} not found"
        )
        if (!isDuplicateNodePair(keep, drop)) {
            throw notADuplicatePair()
        }
        if (request.keepUpdatedAt != entityTimestamp(keep.updatedAt, keep.createdAt) ||
            request.dropUpdatedAt != entityTimestamp(drop.updatedAt, drop.createdAt)
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Concurrent modification")
        }
        if (nodesRepository.existsByParentNode_Id(drop.id!!)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Drop node still has children")
        }

        val keepIncidentLinks = linksRepository.findByModelIdAndEndpointNodeId(modelId, keep.id!!)
        val dropIncidentLinks = linksRepository.findByModelIdAndEndpointNodeId(modelId, drop.id!!)
        val uniqueLinks = mutableListOf<Links>()
        val linksToDelete = mutableListOf<Links>()
        for (link in dropIncidentLinks) {
            when (classify(drop, keep, link, keepIncidentLinks)) {
                Kind.UNIQUE -> uniqueLinks.add(link)
                Kind.MATCHING, Kind.AB -> linksToDelete.add(link)
            }
        }
        val uniqueIds = uniqueLinks.map { it.id!! }.toSet()
        if (!uniqueIds.containsAll(request.transferLinkIds)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "transferLinkIds must be a subset of unique drop links"
            )
        }
        val transferIds = request.transferLinkIds.toSet()
        val transferLinks = uniqueLinks.filter { it.id in transferIds }
        val deletedLinks = uniqueLinks.filter { it.id !in transferIds } + linksToDelete
        val deletedLinkIds = deletedLinks.map { it.id!! }.toSet()

        val affectedDiagramIds = linkedSetOf<UUID>()
        diagramsForNode(modelId, keep.id!!).forEach { affectedDiagramIds.add(it.diagramId) }
        diagramsForNode(modelId, drop.id!!).forEach { affectedDiagramIds.add(it.diagramId) }
        for (linkId in deletedLinkIds) {
            diagramsForLink(modelId, linkId).forEach { affectedDiagramIds.add(it.diagramId) }
        }
        val modelDiagrams = diagramsRepository.findAllActiveByModelId(modelId)
        for (diagram in modelDiagrams) {
            if (diagram.node?.id == drop.id) {
                affectedDiagramIds.add(diagram.id!!)
            }
        }

        val now = Instant.now()
        val currentUserId = CurrentUser.getId()
        for (diagramId in affectedDiagramIds) {
            val lock = diagramEditLocksRepository.findActiveWithDiagram(diagramId, now) ?: continue
            if (lock.lockedBy.id != currentUserId) {
                throw ResponseStatusException(HttpStatus.CONFLICT, DIAGRAM_LOCK_HELD_BY_ANOTHER_USER)
            }
        }

        keep.attrs = replaceTypeProperties(keep.attrs, request.typeProperties)
        nodesRepository.save(keep)

        for (link in transferLinks) {
            if (link.source.id == drop.id) {
                link.source = keep
            }
            if (link.target.id == drop.id) {
                link.target = keep
            }
            linksRepository.save(link)
        }
        for (link in deletedLinks) {
            linksRepository.delete(link)
        }

        val mutatedDiagramIds = mutableListOf<UUID>()
        for (diagram in modelDiagrams) {
            if (diagram.id !in affectedDiagramIds) continue
            var changed = false
            var attrs = remapDropInstancesToKeep(diagram.attrs, drop.id!!, keep.id!!)
            if (deletedLinkIds.isNotEmpty()) {
                attrs = DiagramCanvasJsonCleanup.cleanupDiagramAttrs(
                    attrs,
                    objectMapper,
                    emptySet(),
                    deletedLinkIds
                )
            }
            if (attrs != diagram.attrs) {
                diagram.attrs = attrs
                changed = true
            }
            if (diagram.node?.id == drop.id) {
                diagram.node = keep
                changed = true
            }
            if (changed) {
                diagramsRepository.save(diagram)
                mutatedDiagramIds.add(diagram.id!!)
            }
        }
        diagramsRepository.flush()
        nodesRepository.delete(drop)

        val events = mutableListOf<ModelSyncEntityEvent>()
        events.add(
            ModelSyncEntityEvent(
                ModelSyncEventType.NODE_UPDATED.wireValue,
                ModelSyncEventType.NODE_UPDATED.entity,
                keep.id!!
            )
        )
        events.add(
            ModelSyncEntityEvent(
                ModelSyncEventType.NODE_DELETED.wireValue,
                ModelSyncEventType.NODE_DELETED.entity,
                drop.id!!
            )
        )
        for (link in transferLinks) {
            events.add(
                ModelSyncEntityEvent(
                    ModelSyncEventType.LINK_UPDATED.wireValue,
                    ModelSyncEventType.LINK_UPDATED.entity,
                    link.id!!
                )
            )
        }
        for (link in deletedLinks) {
            events.add(
                ModelSyncEntityEvent(
                    ModelSyncEventType.LINK_DELETED.wireValue,
                    ModelSyncEventType.LINK_DELETED.entity,
                    link.id!!
                )
            )
        }
        for (diagramId in mutatedDiagramIds) {
            events.add(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_UPDATED.wireValue,
                    ModelSyncEventType.DIAGRAM_UPDATED.entity,
                    diagramId
                )
            )
        }
        modelSyncBroadcaster.broadcastModelChanged(modelId, "validation_merge_nodes", events)
        return MergeNodesResponse(keepId = keep.id!!, dropId = drop.id!!)
    }

    fun previewLinks(modelId: UUID, keepId: UUID, dropId: UUID): MergeLinksPreviewResponse {
        requireViewableModel(modelId)
        if (keepId == dropId) {
            throw notADuplicatePair()
        }
        val links = linksRepository.findByModel_IdAndIdIn(modelId, listOf(keepId, dropId))
            .associateBy { requireNotNull(it.id) }
        val keep = links[keepId] ?: throw linkNotFound(keepId)
        val drop = links[dropId] ?: throw linkNotFound(dropId)
        if (!isDuplicateLinkPair(keep, drop)) {
            throw notADuplicatePair()
        }
        return MergeLinksPreviewResponse(
            keepId = keep.id!!,
            dropId = drop.id!!,
            keepTypeProperties = parseTypeProperties(keep.attrs),
            dropTypeProperties = parseTypeProperties(drop.attrs),
            keepDiagrams = diagramsForLink(modelId, keep.id!!),
            dropDiagrams = diagramsForLink(modelId, drop.id!!),
            keepUpdatedAt = entityTimestamp(keep.updatedAt, keep.createdAt),
            dropUpdatedAt = entityTimestamp(drop.updatedAt, drop.createdAt)
        )
    }

    @Transactional
    fun mergeLinks(modelId: UUID, request: MergeLinksRequest): MergeLinksResponse {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanEditModel(model)
        if (request.keepId == request.dropId) {
            throw notADuplicatePair()
        }
        val links = linksRepository.findByModel_IdAndIdIn(modelId, listOf(request.keepId, request.dropId))
            .associateBy { requireNotNull(it.id) }
        val keep = links[request.keepId] ?: throw linkNotFound(request.keepId)
        val drop = links[request.dropId] ?: throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "Link ${request.dropId} not found"
        )
        if (!isDuplicateLinkPair(keep, drop)) {
            throw notADuplicatePair()
        }
        if (request.keepUpdatedAt != entityTimestamp(keep.updatedAt, keep.createdAt) ||
            request.dropUpdatedAt != entityTimestamp(drop.updatedAt, drop.createdAt)
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Concurrent modification")
        }

        val affectedDiagramIds = linkedSetOf<UUID>()
        diagramsForLink(modelId, keep.id!!).forEach { affectedDiagramIds.add(it.diagramId) }
        diagramsForLink(modelId, drop.id!!).forEach { affectedDiagramIds.add(it.diagramId) }

        val now = Instant.now()
        val currentUserId = CurrentUser.getId()
        for (diagramId in affectedDiagramIds) {
            val lock = diagramEditLocksRepository.findActiveWithDiagram(diagramId, now) ?: continue
            if (lock.lockedBy.id != currentUserId) {
                throw ResponseStatusException(HttpStatus.CONFLICT, DIAGRAM_LOCK_HELD_BY_ANOTHER_USER)
            }
        }

        keep.attrs = replaceTypeProperties(keep.attrs, request.typeProperties)
        linksRepository.save(keep)

        val modelDiagrams = diagramsRepository.findAllActiveByModelId(modelId)
        val mutatedDiagramIds = mutableListOf<UUID>()
        for (diagram in modelDiagrams) {
            if (diagram.id !in affectedDiagramIds) continue
            val attrs = remapDropLinkEdgesToKeep(diagram.attrs, drop.id!!, keep.id!!)
            if (attrs != diagram.attrs) {
                diagram.attrs = attrs
                diagramsRepository.save(diagram)
                mutatedDiagramIds.add(diagram.id!!)
            }
        }
        diagramsRepository.flush()
        linksRepository.delete(drop)

        val events = mutableListOf<ModelSyncEntityEvent>()
        events.add(
            ModelSyncEntityEvent(
                ModelSyncEventType.LINK_UPDATED.wireValue,
                ModelSyncEventType.LINK_UPDATED.entity,
                keep.id!!
            )
        )
        events.add(
            ModelSyncEntityEvent(
                ModelSyncEventType.LINK_DELETED.wireValue,
                ModelSyncEventType.LINK_DELETED.entity,
                drop.id!!
            )
        )
        for (diagramId in mutatedDiagramIds) {
            events.add(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_UPDATED.wireValue,
                    ModelSyncEventType.DIAGRAM_UPDATED.entity,
                    diagramId
                )
            )
        }
        modelSyncBroadcaster.broadcastModelChanged(modelId, "validation_merge_links", events)
        return MergeLinksResponse(keepId = keep.id!!, dropId = drop.id!!)
    }

    private fun requireViewableModel(modelId: UUID) {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)
    }

    private fun isDuplicateNodePair(keep: Nodes, drop: Nodes): Boolean {
        if (keep.nodeType.id != drop.nodeType.id) return false
        if (keep.nodeType.name.equals(DIRECTORY_TYPE_NAME, ignoreCase = true)) return false
        return keep.name.trim().lowercase() == drop.name.trim().lowercase()
    }

    private fun isDuplicateLinkPair(keep: Links, drop: Links): Boolean =
        keep.source.id == drop.source.id &&
            keep.target.id == drop.target.id &&
            keep.linkType.id == drop.linkType.id

    private fun classify(
        drop: Nodes,
        keep: Nodes,
        link: Links,
        keepIncidentLinks: List<Links>
    ): Kind {
        val a = keep.id!!
        val b = drop.id!!
        if ((link.source.id == a && link.target.id == b) || (link.source.id == b && link.target.id == a)) {
            return Kind.AB
        }
        val other = if (link.source.id == b) link.target.id!! else link.source.id!!
        val outgoing = link.source.id == b
        val existsOnKeep = keepIncidentLinks.any {
            it.linkType.id == link.linkType.id &&
                if (outgoing) it.source.id == a && it.target.id == other
                else it.source.id == other && it.target.id == a
        }
        return if (existsOnKeep) Kind.MATCHING else Kind.UNIQUE
    }

    private fun toPreviewIncidentLink(drop: Nodes, link: Links): PreviewIncidentLink {
        val outgoing = link.source.id == drop.id
        val other = if (outgoing) link.target else link.source
        return PreviewIncidentLink(
            id = link.id!!,
            linkTypeId = link.linkType.id!!,
            linkTypeName = link.linkType.name,
            direction = if (outgoing) "out" else "in",
            otherNodeId = other.id!!,
            otherNodeName = other.name
        )
    }

    private fun diagramsForNode(modelId: UUID, nodeId: UUID): List<DiagramRef> =
        diagramsForPath(modelId, """exists($.instances.nodes[*] ? (@.modelNodeId == "$nodeId"))""")

    private fun diagramsForLink(modelId: UUID, linkId: UUID): List<DiagramRef> =
        diagramsForPath(modelId, """exists($.instances.edges[*] ? (@.modelLinkId == "$linkId"))""")

    private fun diagramsForPath(modelId: UUID, jsonPath: String): List<DiagramRef> =
        diagramsRepository.findDiagramReferences(modelId, jsonPath, PREVIEW_DIAGRAM_PAGE)
            .content
            .map { DiagramRef(diagramId = it.getId(), diagramName = it.getName()) }

    private fun replaceTypeProperties(attrs: String?, typeProperties: Map<String, Any?>): String {
        val root = if (attrs.isNullOrBlank()) {
            objectMapper.createObjectNode()
        } else {
            try {
                objectMapper.readTree(attrs) as? ObjectNode ?: objectMapper.createObjectNode()
            } catch (_: Exception) {
                objectMapper.createObjectNode()
            }
        }
        root.set<JsonNode>("typeProperties", objectMapper.valueToTree(typeProperties))
        return objectMapper.writeValueAsString(root)
    }

    private fun remapDropInstancesToKeep(attrs: String?, dropId: UUID, keepId: UUID): String? {
        if (attrs.isNullOrBlank()) return attrs
        val root = try {
            objectMapper.readTree(attrs) as? ObjectNode ?: return attrs
        } catch (_: Exception) {
            return attrs
        }
        val from = dropId.toString()
        val to = keepId.toString()
        var changed = false
        fun remap(container: ObjectNode?) {
            val nodes = container?.get("nodes") ?: return
            if (!nodes.isArray) return
            for (node in nodes) {
                if (node is ObjectNode && node.path("modelNodeId").asText(null) == from) {
                    node.put("modelNodeId", to)
                    changed = true
                }
            }
        }
        remap(root)
        val instances = root.get("instances")
        if (instances is ObjectNode) {
            remap(instances)
        }
        return if (changed) objectMapper.writeValueAsString(root) else attrs
    }

    private fun remapDropLinkEdgesToKeep(attrs: String?, dropId: UUID, keepId: UUID): String? {
        if (attrs.isNullOrBlank()) return attrs
        val root = try {
            objectMapper.readTree(attrs) as? ObjectNode ?: return attrs
        } catch (_: Exception) {
            return attrs
        }
        val from = dropId.toString()
        val to = keepId.toString()
        var changed = false
        fun remap(container: ObjectNode?) {
            val edges = container?.get("edges") ?: return
            if (!edges.isArray) return
            val edgesArray = edges as ArrayNode
            val hasKeep = edgesArray.any { edge ->
                edge is ObjectNode && edge.path("modelLinkId").asText(null) == to
            }
            val next = objectMapper.createArrayNode()
            var localChanged = false
            for (edge in edgesArray) {
                if (edge !is ObjectNode) {
                    next.add(edge)
                    continue
                }
                if (edge.path("modelLinkId").asText(null) == from) {
                    if (hasKeep) {
                        localChanged = true
                        continue
                    }
                    edge.put("modelLinkId", to)
                    localChanged = true
                }
                next.add(edge)
            }
            if (localChanged) {
                container.replace("edges", next)
                changed = true
            }
        }
        remap(root)
        val instances = root.get("instances")
        if (instances is ObjectNode) {
            remap(instances)
        }
        return if (changed) objectMapper.writeValueAsString(root) else attrs
    }

    private fun parseTypeProperties(attrs: String?): Map<String, Any?> {
        if (attrs.isNullOrBlank()) return emptyMap()
        val root = objectMapper.readTree(attrs)
        val properties = root.get("typeProperties") ?: return emptyMap()
        if (!properties.isObject) return emptyMap()
        return objectMapper.convertValue(properties, TYPE_PROPERTIES)
    }

    private fun hasDocuments(attrs: String?): Boolean {
        if (attrs.isNullOrBlank()) return false
        val documentFileId = objectMapper.readTree(attrs).get("documentFileId") ?: return false
        return documentFileId.isTextual && documentFileId.asText().isNotBlank()
    }

    private fun entityTimestamp(updatedAt: Instant?, createdAt: Instant?): Instant =
        updatedAt ?: createdAt ?: Instant.EPOCH

    private fun notADuplicatePair(): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, "keepId and dropId must be a duplicate pair")

    private fun nodeNotFound(id: UUID): ResponseStatusException =
        ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")

    private fun linkNotFound(id: UUID): ResponseStatusException =
        ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")

    private enum class Kind { UNIQUE, MATCHING, AB }

    private companion object {
        const val DIRECTORY_TYPE_NAME = "directory"
        val PREVIEW_DIAGRAM_PAGE = PageRequest.of(0, 10_000)
        val TYPE_PROPERTIES = object : TypeReference<Map<String, Any?>>() {}
    }
}
