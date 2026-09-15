package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.model.DeleteUnusedRequest
import ru.kavader.arepos.dto.model.DeleteUnusedResponse
import ru.kavader.arepos.dto.model.AutoMergeLockStatus
import ru.kavader.arepos.dto.model.MergeLinksPreviewResponse
import ru.kavader.arepos.dto.model.MergeLinksRequest
import ru.kavader.arepos.dto.model.MergeLinksResponse
import ru.kavader.arepos.dto.model.MergeNodesPreviewResponse
import ru.kavader.arepos.dto.model.MergeNodesRequest
import ru.kavader.arepos.dto.model.MergeNodesResponse
import ru.kavader.arepos.dto.model.ValidationReportResponse
import ru.kavader.arepos.dto.oef.OefMergeDecisionDto
import ru.kavader.arepos.dto.oef.OefMergeDecisionSaveRequest
import ru.kavader.arepos.dto.oef.OefMergeDecisionSaveResponse
import ru.kavader.arepos.service.ModelValidationLockService
import ru.kavader.arepos.service.ModelValidationMergeService
import ru.kavader.arepos.service.ModelValidationReportService
import ru.kavader.arepos.service.OefMergeDecisionsService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models/{modelId}")
@Tag(name = "Model validation", description = "Model validation report and duplicate merge")
class ModelValidationController(
    private val reportService: ModelValidationReportService,
    private val mergeService: ModelValidationMergeService,
    private val oefMergeDecisionsService: OefMergeDecisionsService,
    private val lockService: ModelValidationLockService
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

    @PostMapping("/validation/unused/delete")
    @Operation(summary = "Delete unused (orphan) nodes and links; every id is re-verified")
    fun deleteUnused(
        @PathVariable modelId: UUID,
        @RequestBody request: DeleteUnusedRequest
    ): DeleteUnusedResponse = reportService.deleteUnused(modelId, request)

    @GetMapping("/oef/merge-decisions")
    @Operation(summary = "List persisted duplicate-merge decisions for OEF imports")
    fun listMergeDecisions(
        @PathVariable modelId: UUID
    ): List<OefMergeDecisionDto> = oefMergeDecisionsService.list(modelId)

    @PutMapping("/oef/merge-decisions")
    @Operation(summary = "Persist duplicate-merge decisions (OEF entity id → target node)")
    fun saveMergeDecisions(
        @PathVariable modelId: UUID,
        @RequestBody request: OefMergeDecisionSaveRequest
    ): OefMergeDecisionSaveResponse = oefMergeDecisionsService.save(modelId, request)

    @DeleteMapping("/oef/merge-decisions/{oefEntityId}")
    @Operation(summary = "Forget a persisted duplicate-merge decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMergeDecision(
        @PathVariable modelId: UUID,
        @PathVariable oefEntityId: String
    ) = oefMergeDecisionsService.delete(modelId, oefEntityId)

    @GetMapping("/validation/auto-merge/lock")
    @Operation(summary = "Current auto-merge lock state for the model")
    fun autoMergeLockStatus(@PathVariable modelId: UUID): AutoMergeLockStatus =
        lockService.status(modelId)

    @PostMapping("/validation/auto-merge/lock")
    @Operation(summary = "Acquire the validation auto-merge lock (ADMIN, model edit permission)")
    fun acquireAutoMergeLock(@PathVariable modelId: UUID): AutoMergeLockStatus =
        lockService.acquire(modelId)

    @DeleteMapping("/validation/auto-merge/lock")
    @Operation(summary = "Release the validation auto-merge lock held by the current user")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun releaseAutoMergeLock(@PathVariable modelId: UUID) = lockService.release(modelId)
}
