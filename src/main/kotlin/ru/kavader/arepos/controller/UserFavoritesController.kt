package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.dashboard.DashboardRecentDiagramItem
import ru.kavader.arepos.dto.favorite.FavoriteDiagramIdsResponse
import ru.kavader.arepos.service.DiagramFavoriteService

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Favorites", description = "Current user's favorite diagrams")
class UserFavoritesController(
    private val diagramFavoriteService: DiagramFavoriteService
) {
    @GetMapping("/favorite-diagram-ids")
    @Operation(summary = "List ids of the current user's favorite diagrams")
    fun listFavoriteDiagramIds(): FavoriteDiagramIdsResponse =
        FavoriteDiagramIdsResponse(ids = diagramFavoriteService.listFavoriteDiagramIds())

    @GetMapping("/favorite-diagrams")
    @Operation(summary = "List the current user's favorite diagrams, most recently favorited first")
    fun listFavoriteDiagrams(pageable: Pageable): Page<DashboardRecentDiagramItem> =
        diagramFavoriteService.listFavoriteDiagrams(pageable)
}
