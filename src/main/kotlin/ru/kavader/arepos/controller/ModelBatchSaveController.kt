package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.dto.model.BatchSaveResponse
import ru.kavader.arepos.service.ModelBatchSaveService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Model Batch Save", description = "Atomic model batch save endpoints")
class ModelBatchSaveController(
    private val batchSaveService: ModelBatchSaveService
) {
    @PostMapping("/{modelId}/batch-save")
    @Operation(summary = "Apply model batch save")
    fun batchSave(
        @PathVariable modelId: UUID,
        @RequestBody @Valid request: BatchSaveRequest
    ): BatchSaveResponse = batchSaveService.batchSave(modelId, request)
}
