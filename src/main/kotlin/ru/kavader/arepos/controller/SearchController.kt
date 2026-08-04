package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.search.CatalogSearchResponse
import ru.kavader.arepos.dto.search.ModelSearchResponse
import ru.kavader.arepos.service.SearchService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Slim search endpoints for agents and MCP")
class SearchController(
    private val searchService: SearchService
) {

    @GetMapping("/catalog")
    @Operation(summary = "Search models and notations by name (slim hits)")
    fun searchCatalog(
        @RequestParam q: String,
        @RequestParam(required = false) kinds: String?,
        @RequestParam(required = false) limit: Int?
    ): CatalogSearchResponse = searchService.searchCatalog(q, kinds, limit)

    @GetMapping("/models/{modelId}")
    @Operation(summary = "Search nodes, links, diagrams inside a model (slim hits)")
    fun searchModel(
        @PathVariable modelId: UUID,
        @RequestParam q: String,
        @RequestParam(required = false) kinds: String?,
        @RequestParam(required = false) limit: Int?
    ): ModelSearchResponse = searchService.searchModel(modelId, q, kinds, limit)
}
