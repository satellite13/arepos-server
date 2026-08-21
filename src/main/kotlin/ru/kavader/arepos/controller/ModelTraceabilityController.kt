package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.model.DiagramReferenceResponse
import ru.kavader.arepos.dto.model.GraphDirection
import ru.kavader.arepos.dto.model.GraphNeighborResponse
import ru.kavader.arepos.service.ModelTraceabilityService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models/{modelId}")
@Tag(name = "Model traceability", description = "Bounded model graph and diagram reference endpoints")
class ModelTraceabilityController(
    private val traceabilityService: ModelTraceabilityService
) {
    @GetMapping("/graph/neighbors")
    @Operation(summary = "Page direct graph neighbors")
    fun graphNeighbors(
        @PathVariable modelId: UUID,
        @RequestParam nodeId: UUID,
        @RequestParam(defaultValue = "both") direction: String,
        @RequestParam(required = false) linkTypeId: UUID?,
        @PageableDefault(size = 50) pageable: Pageable
    ): Page<GraphNeighborResponse> = traceabilityService.graphNeighbors(
        modelId,
        nodeId,
        GraphDirection.parse(direction),
        linkTypeId,
        pageable
    )

    @GetMapping("/diagram-references")
    @Operation(summary = "Page active diagrams containing a model node instance")
    fun diagramReferences(
        @PathVariable modelId: UUID,
        @RequestParam nodeId: UUID,
        @PageableDefault(size = 50) pageable: Pageable
    ): Page<DiagramReferenceResponse> =
        traceabilityService.diagramReferences(modelId, nodeId, pageable)
}
