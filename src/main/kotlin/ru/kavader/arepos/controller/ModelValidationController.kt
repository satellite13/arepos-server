package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.model.MergeLinksPreviewResponse
import ru.kavader.arepos.dto.model.MergeLinksRequest
import ru.kavader.arepos.dto.model.MergeLinksResponse
import ru.kavader.arepos.dto.model.MergeNodesPreviewResponse
import ru.kavader.arepos.dto.model.MergeNodesRequest
import ru.kavader.arepos.dto.model.MergeNodesResponse
import ru.kavader.arepos.dto.model.ValidationReportResponse
import ru.kavader.arepos.service.ModelValidationMergeService
import ru.kavader.arepos.service.ModelValidationReportService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models/{modelId}")
@Tag(name = "Model validation", description = "Model validation report and duplicate merge")
class ModelValidationController(
    private val reportService: ModelValidationReportService,
    private val mergeService: ModelValidationMergeService
) {
    @GetMapping("/validation-report")
    @Operation(summary = "Report duplicate nodes and directed links")
    fun report(@PathVariable modelId: UUID): ValidationReportResponse =
        reportService.report(modelId)

    @GetMapping("/validation/merge-nodes-preview")
    @Operation(summary = "Preview merging a duplicate node pair")
    fun previewNodes(
        @PathVariable modelId: UUID,
        @RequestParam keepId: UUID,
        @RequestParam dropId: UUID
    ): MergeNodesPreviewResponse = mergeService.previewNodes(modelId, keepId, dropId)

    @GetMapping("/validation/merge-links-preview")
    @Operation(summary = "Preview merging a duplicate link pair")
    fun previewLinks(
        @PathVariable modelId: UUID,
        @RequestParam keepId: UUID,
        @RequestParam dropId: UUID
    ): MergeLinksPreviewResponse = mergeService.previewLinks(modelId, keepId, dropId)

    @PostMapping("/validation/merge-nodes")
    @Operation(summary = "Merge a duplicate node pair")
    fun mergeNodes(
        @PathVariable modelId: UUID,
        @RequestBody request: MergeNodesRequest
    ): MergeNodesResponse = mergeService.mergeNodes(modelId, request)

    @PostMapping("/validation/merge-links")
    @Operation(summary = "Merge a duplicate directed link pair")
    fun mergeLinks(
        @PathVariable modelId: UUID,
        @RequestBody request: MergeLinksRequest
    ): MergeLinksResponse = mergeService.mergeLinks(modelId, request)
}
