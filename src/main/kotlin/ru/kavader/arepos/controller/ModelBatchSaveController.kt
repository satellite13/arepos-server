package ru.kavader.arepos.controller

import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.dto.model.BatchSaveResponse
import ru.kavader.arepos.service.ModelBatchSaveService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
class ModelBatchSaveController(
    private val batchSaveService: ModelBatchSaveService
) {
    @PostMapping("/{modelId}/batch-save")
    fun batchSave(
        @PathVariable modelId: UUID,
        @RequestBody request: BatchSaveRequest
    ): BatchSaveResponse = batchSaveService.batchSave(modelId, request)
}
