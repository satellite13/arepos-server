package ru.kavader.arepos.security.access

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.CurrentUser
import java.util.UUID

@Component
class NotationDiagramAccess(
    private val diagramsRepository: DiagramsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val topLevelAccess: TopLevelAccess
) {
    fun filterViewableNotations(notations: Collection<Notations>): List<Notations> {
        if (notations.isEmpty()) {
            return emptyList()
        }
        val live = notations.filter { !it.deleted }
        if (live.isEmpty()) {
            return emptyList()
        }
        val direct = topLevelAccess.canViewNotationsDirect(live)
        val userId = currentUserId()
        return live.filter { notation ->
            val id = notation.id ?: return@filter false
            direct[id] == true || diagramsRepository.existsViewableModelDiagramWithNotation(id, userId)
        }
    }

    fun canViewNotation(notation: Notations): Boolean {
        if (notation.deleted) return false
        val id = notation.id ?: return false
        return topLevelAccess.canViewNotationDirect(notation) ||
            diagramsRepository.existsViewableModelDiagramWithNotation(id, currentUserId())
    }

    fun canViewNodeType(nodeType: NodeTypes): Boolean {
        val id = nodeType.id ?: return false
        return topLevelAccess.canViewNodeTypeDirect(nodeType) ||
            componentsRepository.existsNodeTypeReachableViaViewableNotation(id, currentUserId())
    }

    fun canViewLinkType(linkType: LinkTypes): Boolean {
        val id = linkType.id ?: return false
        return topLevelAccess.canViewLinkTypeDirect(linkType) ||
            relationsRepository.existsLinkTypeReachableViaViewableNotation(id, currentUserId())
    }

    fun canViewDiagrams(diagrams: Collection<Diagrams>): Map<UUID, Boolean> {
        if (diagrams.isEmpty()) {
            return emptyMap()
        }
        val modelView = topLevelAccess.canViewModels(diagrams.map { it.model }.distinctBy { it.id })
        return diagrams.mapNotNull { diagram ->
            val diagramId = diagram.id ?: return@mapNotNull null
            val modelId = diagram.model.id ?: return@mapNotNull diagramId to false
            diagramId to (modelView[modelId] == true)
        }.toMap()
    }

    fun filterViewableDiagrams(diagrams: Collection<Diagrams>): List<Diagrams> {
        val decisions = canViewDiagrams(diagrams)
        return diagrams.filter { diagram -> diagram.id?.let { decisions[it] } == true }
    }

    fun canUseNotationInModelDiagramEditor(notation: Notations, model: Models): Boolean {
        if (canViewNotation(notation)) {
            return true
        }
        val notationId = notation.id ?: return false
        val modelId = model.id ?: return false
        return topLevelAccess.canEditModel(model) &&
            diagramsRepository.existsByModelIdAndNotationIdAndDeletedFalse(modelId, notationId)
    }

    private fun currentUserId(): UUID = CurrentUser.getId()
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")
}
