package ru.kavader.arepos.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.controller.toPage
import ru.kavader.arepos.dto.dashboard.DashboardRecentDiagramItem
import ru.kavader.arepos.mapper.AuditMapper
import ru.kavader.arepos.model.UserDiagramFavorite
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.UserDiagramFavoriteRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class DiagramFavoriteService(
    private val userDiagramFavoriteRepository: UserDiagramFavoriteRepository,
    private val diagramsRepository: DiagramsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val auditMapper: AuditMapper
) {
    @Transactional
    fun addFavorite(diagramId: UUID) {
        val diagram = diagramsRepository.findById(diagramId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $diagramId not found")
        }
        accessService.requireCanViewDiagram(diagram)
        val userId = accessService.currentUserId()
        if (!userDiagramFavoriteRepository.existsByDiagramIdAndUserId(diagramId, userId)) {
            val user = usersRepository.findById(userId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User $userId not found")
            }
            userDiagramFavoriteRepository.save(
                UserDiagramFavorite(
                    user = user,
                    diagram = diagram,
                    createdAt = Instant.now()
                )
            )
        }
    }

    @Transactional
    fun removeFavorite(diagramId: UUID) {
        userDiagramFavoriteRepository.deleteByDiagramIdAndUserId(diagramId, accessService.currentUserId())
    }

    @Transactional(readOnly = true)
    fun listFavoriteDiagramIds(): List<UUID> =
        userDiagramFavoriteRepository.findActiveDiagramIdsByUserId(accessService.currentUserId())

    @Transactional(readOnly = true)
    fun listFavoriteDiagrams(pageable: Pageable): Page<DashboardRecentDiagramItem> {
        val diagrams = userDiagramFavoriteRepository
            .findActiveFavoritesWithDiagramAndModelByUserId(accessService.currentUserId())
            .map { it.diagram }
        val viewable = if (accessService.canViewAdminPanel()) {
            diagrams
        } else {
            accessService.filterViewableDiagrams(diagrams)
        }
        val safePageable = PageRequest.of(
            pageable.pageNumber.coerceAtLeast(0),
            pageable.pageSize.coerceIn(1, MAX_PAGE_SIZE)
        )
        return viewable.toPage(safePageable).map { auditMapper.toRecentDiagramItem(it) }
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
