package ru.kavader.arepos.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramReferenceResponse
import ru.kavader.arepos.dto.model.GraphDirection
import ru.kavader.arepos.dto.model.GraphNeighborResponse
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@Service
class ModelTraceabilityService(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val traceabilityReader: ModelTraceabilityReader
) {
    fun graphNeighbors(
        modelId: UUID,
        nodeId: UUID,
        direction: GraphDirection,
        linkTypeId: UUID?,
        pageable: Pageable
    ): Page<GraphNeighborResponse> {
        requireCanViewModel(modelId)
        return traceabilityReader.graphNeighbors(
            modelId,
            nodeId,
            direction,
            linkTypeId,
            bounded(pageable)
        )
    }

    fun diagramReferences(
        modelId: UUID,
        nodeId: UUID,
        pageable: Pageable
    ): Page<DiagramReferenceResponse> {
        requireCanViewModel(modelId)
        return traceabilityReader.diagramReferences(modelId, nodeId, bounded(pageable))
    }

    fun diagramReferencesForLink(
        modelId: UUID,
        linkId: UUID,
        pageable: Pageable
    ): Page<DiagramReferenceResponse> {
        requireCanViewModel(modelId)
        return traceabilityReader.diagramReferencesForLink(modelId, linkId, bounded(pageable))
    }

    private fun requireCanViewModel(modelId: UUID) {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)
    }

    private fun bounded(pageable: Pageable): Pageable =
        PageRequest.of(pageable.pageNumber, pageable.pageSize.coerceIn(1, MAX_PAGE_SIZE))

    private companion object {
        const val MAX_PAGE_SIZE = 50
    }
}
