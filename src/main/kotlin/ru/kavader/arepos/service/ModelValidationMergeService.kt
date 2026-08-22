package ru.kavader.arepos.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramRef
import ru.kavader.arepos.dto.model.MergeLinksPreviewResponse
import ru.kavader.arepos.dto.model.MergeNodesPreviewResponse
import ru.kavader.arepos.dto.model.PreviewIncidentLink
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
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
