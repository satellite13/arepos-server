package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.ModelDiffResponse
import ru.kavader.arepos.service.ModelDiffService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Model Diff", description = "Model comparison endpoints")
class ModelDiffController(
    private val modelDiffService: ModelDiffService,
    private val modelsRepository: ModelsRepository,
    private val accessService: ResourceAccessService
) {

    @GetMapping("/{baseId}/diff/{targetId}")
    @Operation(summary = "Compare two model versions")
    fun diffModels(
        @PathVariable baseId: UUID,
        @PathVariable targetId: UUID
    ): ModelDiffResponse {
        val baseModel = modelsRepository.findById(baseId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $baseId not found")
        }
        val targetModel = modelsRepository.findById(targetId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $targetId not found")
        }
        accessService.requireCanViewModel(baseModel)
        accessService.requireCanViewModel(targetModel)

        return modelDiffService.computeDiff(baseId, targetId)
    }
}
