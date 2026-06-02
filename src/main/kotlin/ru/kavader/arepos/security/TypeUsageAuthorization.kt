package ru.kavader.arepos.security

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.service.ModelDiagramTypeValidator

@Service
class TypeUsageAuthorization(
    private val accessService: ResourceAccessService,
    private val typeValidator: ModelDiagramTypeValidator
) {
    fun requireCanUseNodeTypeForNotation(nodeType: NodeTypes, notation: Notations) {
        if (accessService.canUseNodeType(nodeType)) return
        if (accessService.canEditNotation(notation) && nodeType.owner.id == notation.owner.id) return
        deny()
    }

    fun requireCanUseLinkTypeForNotation(linkType: LinkTypes, notation: Notations) {
        if (accessService.canUseLinkType(linkType)) return
        if (accessService.canEditNotation(notation) && linkType.owner.id == notation.owner.id) return
        deny()
    }

    fun requireCanUseNodeTypeForModel(nodeType: NodeTypes, model: Models) {
        if (accessService.canUseNodeType(nodeType)) return
        val canEditModel = accessService.canEditModel(model)
        if (canEditModel && nodeType.owner.id == model.owner.id) return
        val nodeTypeId = nodeType.id ?: deny()
        val modelId = model.id ?: deny()
        if (canEditModel && typeValidator.isNodeTypeUsedInModelDiagramNotations(nodeTypeId, modelId)) return
        deny()
    }

    fun requireCanUseLinkTypeForModel(linkType: LinkTypes, model: Models) {
        if (accessService.canUseLinkType(linkType)) return
        val canEditModel = accessService.canEditModel(model)
        if (canEditModel && linkType.owner.id == model.owner.id) return
        val linkTypeId = linkType.id ?: deny()
        val modelId = model.id ?: deny()
        if (canEditModel && typeValidator.isLinkTypeUsedInModelDiagramNotations(linkTypeId, modelId)) return
        deny()
    }

    private fun deny(): Nothing =
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
}
