package ru.kavader.arepos.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramReferenceResponse
import ru.kavader.arepos.dto.model.GraphDirection
import ru.kavader.arepos.dto.model.GraphNeighborResponse
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodesRepository
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ModelTraceabilityReader(
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val modelMapper: ModelMapper
) {
    fun graphNeighbors(
        modelId: UUID,
        nodeId: UUID,
        direction: GraphDirection,
        linkTypeId: UUID?,
        pageable: Pageable
    ): Page<GraphNeighborResponse> {
        requireModelNode(modelId, nodeId)
        val rows = linksRepository.findGraphNeighborIds(
            modelId,
            nodeId,
            direction.name,
            linkTypeId,
            pageable
        )
        if (rows.isEmpty) {
            return rows.map { error("Unreachable empty graph row") }
        }

        val linkIds = rows.content.map { it.getLinkId() }
        val nodeIds = rows.content.map { it.getNodeId() }.distinct()
        val linksById = linksRepository.findByModel_IdAndIdIn(modelId, linkIds)
            .associateBy { requireNotNull(it.id) }
        val nodesById = nodesRepository.findByModel_IdAndIdIn(modelId, nodeIds)
            .associateBy { requireNotNull(it.id) }
        return rows.map { row ->
            val link = linksById[row.getLinkId()]
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Graph link is missing")
            val node = nodesById[row.getNodeId()]
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Graph neighbor node is missing")
            GraphNeighborResponse(modelMapper.toResponse(link), modelMapper.toResponse(node))
        }
    }

    fun diagramReferences(
        modelId: UUID,
        nodeId: UUID,
        pageable: Pageable
    ): Page<DiagramReferenceResponse> {
        requireModelNode(modelId, nodeId)
        return diagramsRepository.findDiagramReferences(
            modelId,
            diagramReferenceJsonPath(nodeId),
            pageable
        ).map {
            DiagramReferenceResponse(
                id = it.getId(),
                name = it.getName(),
                version = it.getVersion(),
                notationId = it.getNotationId(),
                nodeId = it.getNodeId()
            )
        }
    }

    private fun requireModelNode(modelId: UUID, nodeId: UUID) {
        if (!nodesRepository.existsByIdAndModel_Id(nodeId, modelId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Node $nodeId not found")
        }
    }

    private fun diagramReferenceJsonPath(nodeId: UUID): String =
        """exists($.instances.nodes[*] ? (@.modelNodeId == "$nodeId"))"""
}
