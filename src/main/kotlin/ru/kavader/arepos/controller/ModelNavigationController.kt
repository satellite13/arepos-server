package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.NodeResponse
import ru.kavader.arepos.service.ModelAncestorService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models/{modelId}")
@Tag(name = "Model navigation", description = "Bounded model tree navigation endpoints")
class ModelNavigationController(
    private val modelAncestorService: ModelAncestorService
) {
    @GetMapping("/nodes/{nodeId}/ancestors")
    @Operation(summary = "List a model node's ancestors")
    fun listAncestors(
        @PathVariable modelId: String,
        @PathVariable nodeId: String
    ): List<NodeResponse> = modelAncestorService.listAncestors(
        parseUuid(modelId, "modelId"),
        parseUuid(nodeId, "nodeId")
    )

    private fun parseUuid(value: String, name: String): UUID = try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$name must be a UUID")
    }
}
