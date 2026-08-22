package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.model.ModelLinksResolveRequest
import ru.kavader.arepos.dto.model.ModelLinksResolveResponse
import ru.kavader.arepos.dto.model.ModelNodesResolveRequest
import ru.kavader.arepos.dto.model.ModelNodesResolveResponse
import ru.kavader.arepos.service.ModelResolveService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models/{modelId}")
@Tag(name = "Model resolve", description = "Bounded model entity resolve endpoints")
class ModelResolveController(
    private val modelResolveService: ModelResolveService
) {
    @PostMapping("/nodes:resolve")
    @Operation(summary = "Resolve model nodes by IDs")
    fun resolveNodes(
        @PathVariable modelId: UUID,
        @RequestBody @Valid request: ModelNodesResolveRequest
    ): ModelNodesResolveResponse = modelResolveService.resolveNodes(modelId, request)

    @PostMapping("/links:resolve")
    @Operation(summary = "Resolve model links by IDs or endpoint node IDs")
    fun resolveLinks(
        @PathVariable modelId: UUID,
        @RequestBody @Valid request: ModelLinksResolveRequest
    ): ModelLinksResolveResponse = modelResolveService.resolveLinks(modelId, request)
}
