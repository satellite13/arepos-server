package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.ModelLinksResolveRequest
import ru.kavader.arepos.dto.model.ModelLinksResolveResponse
import ru.kavader.arepos.dto.model.ModelNodesResolveRequest
import ru.kavader.arepos.dto.model.ModelNodesResolveResponse
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@Service
class ModelResolveService(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val accessService: ResourceAccessService,
    private val modelMapper: ModelMapper
) {
    @Transactional(readOnly = true)
    fun resolveNodes(modelId: UUID, request: ModelNodesResolveRequest): ModelNodesResolveResponse {
        requireCanViewModel(modelId)
        val requestedIds = request.nodeIds.distinct()
        val nodesById = nodesRepository.findByModel_IdAndIdIn(modelId, requestedIds)
            .associateBy { requireNotNull(it.id) }

        return ModelNodesResolveResponse(
            nodes = requestedIds.mapNotNull(nodesById::get).map(modelMapper::toResponse),
            missingIds = requestedIds.filterNot(nodesById::containsKey)
        )
    }

    @Transactional(readOnly = true)
    fun resolveLinks(modelId: UUID, request: ModelLinksResolveRequest): ModelLinksResolveResponse {
        requireCanViewModel(modelId)
        val requestedLinkIds = request.linkIds.distinct()
        val explicitById = if (requestedLinkIds.isEmpty()) {
            emptyMap()
        } else {
            linksRepository.findByModel_IdAndIdIn(modelId, requestedLinkIds)
                .associateBy { requireNotNull(it.id) }
        }

        val resolvedById = linkedMapOf<UUID, ru.kavader.arepos.model.Links>()
        requestedLinkIds.mapNotNull(explicitById::get).forEach { link ->
            resolvedById[requireNotNull(link.id)] = link
        }

        val endpointNodeIds = request.endpointNodeIds.distinct()
        if (endpointNodeIds.isNotEmpty()) {
            linksRepository.findByModelIdAndEndpointNodeIds(modelId, endpointNodeIds)
                .forEach { link -> resolvedById.putIfAbsent(requireNotNull(link.id), link) }
        }

        return ModelLinksResolveResponse(
            links = resolvedById.values.map(modelMapper::toResponse),
            missingLinkIds = requestedLinkIds.filterNot(explicitById::containsKey)
        )
    }

    private fun requireCanViewModel(modelId: UUID) {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)
    }
}
