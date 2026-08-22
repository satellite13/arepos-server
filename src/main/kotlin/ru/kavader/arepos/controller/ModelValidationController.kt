package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.model.ValidationReportResponse
import ru.kavader.arepos.service.ModelValidationReportService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models/{modelId}")
@Tag(name = "Model validation", description = "Read-only model validation report")
class ModelValidationController(
    private val reportService: ModelValidationReportService
) {
    @GetMapping("/validation-report")
    @Operation(summary = "Report duplicate nodes and directed links")
    fun report(@PathVariable modelId: UUID): ValidationReportResponse =
        reportService.report(modelId)
}
