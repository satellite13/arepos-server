package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.ModelLinksResolveRequest
import ru.kavader.arepos.dto.model.ModelLinksResolveResponse
import ru.kavader.arepos.dto.model.ModelNodesResolveRequest
import ru.kavader.arepos.dto.model.ModelNodesResolveResponse
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@Service
class ModelResolveService(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val resolveReader: ModelResolveReader
) {
    fun resolveNodes(modelId: UUID, request: ModelNodesResolveRequest): ModelNodesResolveResponse {
        requireCanViewModel(modelId)
        return resolveReader.resolveNodes(modelId, request)
    }

    fun resolveLinks(modelId: UUID, request: ModelLinksResolveRequest): ModelLinksResolveResponse {
        requireCanViewModel(modelId)
        return resolveReader.resolveLinks(modelId, request)
    }

    private fun requireCanViewModel(modelId: UUID) {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)
    }
}
