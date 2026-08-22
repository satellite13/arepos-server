package ru.kavader.arepos.service

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.MODEL_LINK_RESOLVE_MAX_RESULTS
import ru.kavader.arepos.dto.model.ModelLinksResolveRequest
import ru.kavader.arepos.dto.model.ModelLinksResolveResponse
import ru.kavader.arepos.dto.model.ModelNodesResolveRequest
import ru.kavader.arepos.dto.model.ModelNodesResolveResponse
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodesRepository
import java.util.UUID

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class ModelResolveReader(
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val modelMapper: ModelMapper
) {
    fun resolveNodes(modelId: UUID, request: ModelNodesResolveRequest): ModelNodesResolveResponse {
        val requestedIds = request.nodeIds.distinct()
        val nodesById = nodesRepository.findByModel_IdAndIdIn(modelId, requestedIds)
            .associateBy { requireNotNull(it.id) }

        return ModelNodesResolveResponse(
            nodes = requestedIds.mapNotNull(nodesById::get).map(modelMapper::toResponse),
            missingIds = requestedIds.filterNot(nodesById::containsKey)
        )
    }

    fun resolveLinks(modelId: UUID, request: ModelLinksResolveRequest): ModelLinksResolveResponse {
        val requestedLinkIds = request.linkIds.distinct()
        val explicitFoundIds = if (requestedLinkIds.isEmpty()) {
            emptySet()
        } else {
            linksRepository.findIdsByModelIdAndIdIn(modelId, requestedLinkIds).toSet()
        }

        val resolvedIds = linkedSetOf<UUID>()
        requestedLinkIds.filterTo(resolvedIds, explicitFoundIds::contains)

        val endpointNodeIds = request.endpointNodeIds.distinct()
        if (endpointNodeIds.isNotEmpty()) {
            val endpointLinkIds = linksRepository.findIdsByModelIdAndEndpointNodeIds(
                modelId = modelId,
                endpointNodeIds = endpointNodeIds,
                pageable = Pageable.ofSize(MODEL_LINK_RESOLVE_MAX_RESULTS + 1)
            )
            if (endpointLinkIds.size > MODEL_LINK_RESOLVE_MAX_RESULTS) {
                throwResultLimitExceeded()
            }
            resolvedIds.addAll(endpointLinkIds)
        }

        if (resolvedIds.size > MODEL_LINK_RESOLVE_MAX_RESULTS) {
            throwResultLimitExceeded()
        }

        val linksById = if (resolvedIds.isEmpty()) {
            emptyMap()
        } else {
            linksRepository.findByModel_IdAndIdIn(modelId, resolvedIds.toList())
                .associateBy { requireNotNull(it.id) }
        }
        return ModelLinksResolveResponse(
            links = resolvedIds.mapNotNull(linksById::get).map(modelMapper::toResponse),
            missingLinkIds = requestedLinkIds.filterNot(explicitFoundIds::contains)
        )
    }

    private fun throwResultLimitExceeded(): Nothing =
        throw ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            MODEL_LINK_RESOLVE_RESULT_LIMIT_EXCEEDED
        )

    private companion object {
        const val MODEL_LINK_RESOLVE_RESULT_LIMIT_EXCEEDED =
            "MODEL_LINK_RESOLVE_RESULT_LIMIT_EXCEEDED"
    }
}
