package ru.kavader.arepos.controller

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

enum class PermissionResourceType {
    MODEL,
    NOTATION,
    DIAGRAM,
    NODE_TYPE,
    LINK_TYPE,
    NODE_SHAPE,
    ADMIN_PANEL
}

enum class PermissionAction {
    VIEW,
    EDIT,
    MANAGE
}

data class PermissionCheckRequest(
    val resourceType: PermissionResourceType? = null,
    val resourceId: UUID? = null,
    val actions: List<PermissionAction> = listOf(PermissionAction.VIEW)
)

data class PermissionCheckResponse(
    val resourceType: PermissionResourceType,
    val resourceId: UUID,
    val decisions: Map<String, Boolean>
)

@RestController
@RequestMapping("/api/v1/permissions")
class PermissionsController(
    private val modelsRepository: ModelsRepository,
    private val notationsRepository: NotationsRepository,
    private val diagramsRepository: DiagramsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val nodeShapesRepository: NodeShapesRepository,
    private val accessService: ResourceAccessService
) {
    @PostMapping("/check")
    fun check(@RequestBody request: PermissionCheckRequest): PermissionCheckResponse {
        val resourceType = request.resourceType ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "resourceType is required"
        )
        val resourceId = request.resourceId ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "resourceId is required"
        )
        val actions = request.actions.ifEmpty { listOf(PermissionAction.VIEW) }.distinct()
        if (actions.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "actions must not be empty")
        }

        val decisions = actions.associate { action ->
            action.name to resolveDecision(resourceType, resourceId, action)
        }

        return PermissionCheckResponse(
            resourceType = resourceType,
            resourceId = resourceId,
            decisions = decisions
        )
    }

    private fun resolveDecision(
        resourceType: PermissionResourceType,
        resourceId: UUID,
        action: PermissionAction
    ): Boolean {
        return when (resourceType) {
            PermissionResourceType.MODEL -> {
                val model = modelsRepository.findById(resourceId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Model $resourceId not found")
                }
                when (action) {
                    PermissionAction.VIEW -> accessService.canViewModel(model)
                    PermissionAction.EDIT -> accessService.canEditModel(model)
                    PermissionAction.MANAGE -> accessService.canManageShares(model.owner.id!!)
                }
            }

            PermissionResourceType.NOTATION -> {
                val notation = notationsRepository.findById(resourceId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $resourceId not found")
                }
                when (action) {
                    PermissionAction.VIEW -> accessService.canViewNotation(notation)
                    PermissionAction.EDIT -> accessService.canEditNotation(notation)
                    PermissionAction.MANAGE -> accessService.canManageShares(notation.owner.id!!)
                }
            }

            PermissionResourceType.DIAGRAM -> {
                val diagram = diagramsRepository.findById(resourceId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $resourceId not found")
                }
                when (action) {
                    PermissionAction.VIEW -> accessService.canViewDiagram(diagram)
                    PermissionAction.EDIT -> accessService.canEditDiagram(diagram)
                    PermissionAction.MANAGE -> accessService.canEditDiagram(diagram)
                }
            }

            PermissionResourceType.NODE_TYPE -> {
                val nodeType = nodeTypesRepository.findById(resourceId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $resourceId not found")
                }
                when (action) {
                    PermissionAction.VIEW -> accessService.canViewNodeType(nodeType)
                    PermissionAction.EDIT -> accessService.canEditNodeType(nodeType)
                    PermissionAction.MANAGE -> accessService.canManageShares(nodeType.owner.id!!)
                }
            }

            PermissionResourceType.LINK_TYPE -> {
                val linkType = linkTypesRepository.findById(resourceId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $resourceId not found")
                }
                when (action) {
                    PermissionAction.VIEW -> accessService.canViewLinkType(linkType)
                    PermissionAction.EDIT -> accessService.canEditLinkType(linkType)
                    PermissionAction.MANAGE -> accessService.canManageShares(linkType.owner.id!!)
                }
            }

            PermissionResourceType.NODE_SHAPE -> {
                val shape = nodeShapesRepository.findById(resourceId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $resourceId not found")
                }
                when (action) {
                    PermissionAction.VIEW -> true
                    PermissionAction.EDIT -> accessService.canEditNodeShape(shape)
                    PermissionAction.MANAGE -> accessService.canManageShares(shape.owner.id!!)
                }
            }

            PermissionResourceType.ADMIN_PANEL -> {
                when (action) {
                    PermissionAction.VIEW -> CurrentUser.isAdmin()
                    PermissionAction.EDIT -> CurrentUser.isAdmin()
                    PermissionAction.MANAGE -> CurrentUser.isAdmin()
                }
            }
        }
    }
}
