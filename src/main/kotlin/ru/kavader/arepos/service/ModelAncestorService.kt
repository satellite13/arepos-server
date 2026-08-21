package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@Service
class ModelAncestorService(
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService,
    private val ancestorReader: ModelAncestorReader
) {
    fun listAncestors(modelId: UUID, nodeId: UUID): List<NodeResponse> {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)
        return ancestorReader.readAncestors(modelId, nodeId)
    }
}
