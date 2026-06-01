package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.notation.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/components")
class ComponentsController(
    private val componentsRepository: ComponentsRepository,
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator
) {

    @GetMapping
    fun listComponents(
        pageable: Pageable,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) tagsAll: String?
    ): Page<ComponentResponse> {
        val normalizedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val tags = parseTags(tagsAll)
        val tagsJson = if (tags.isEmpty()) null else tags.toJsonArray()

        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            return componentsRepository.findAccessibleByFiltersForUser(
                notationId = notationId,
                ownerId = ownerId,
                name = normalizedName,
                tagsJson = tagsJson,
                currentUserId = currentUserId,
                diagramEditorModelId = modelId,
                pageable = pageable
            ).map { it.toResponse(accessService) }
        }

        val components = componentsRepository.findByFilters(
            notationId = notationId,
            ownerId = ownerId,
            name = normalizedName,
            tagsJson = tagsJson,
            pageable = pageable
        )
        return components.map { it.toResponse(accessService) }
    }

    @GetMapping("/{id}")
    fun getComponent(@PathVariable id: UUID): ComponentResponse =
        componentsRepository.findById(id)
            .map {
                accessService.requireCanViewComponent(it)
                it.toResponse(accessService)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createComponent(@RequestBody request: ComponentRequest): ComponentResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        accessService.requireCanEditNotation(notation)
        val nodeType = nodeTypesRepository.findById(request.nodeTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType ${request.nodeTypeId} not found")
            }
        requireCanUseNodeTypeForNotation(nodeType, notation)
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = componentsRepository.save(
            Components(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                version = request.version,
                notation = notation,
                owner = owner,
                nodeType = nodeType
            )
        )
        return saved.toResponse(accessService)
    }

    @PutMapping("/{id}")
    fun updateComponent(
        @PathVariable id: UUID,
        @RequestBody request: ComponentUpdateRequest
    ): ComponentResponse {
        val component = componentsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $id not found")
            }
        accessService.requireCanEditComponent(component)

        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, component.owner)
        val notation = request.notationId?.let {
            notationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $it not found")
            }
        }?.also { newNotation ->
            accessService.requireCanEditNotation(newNotation)
        } ?: component.notation
        val nodeType = request.nodeTypeId?.let {
            nodeTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $it not found")
            }
        }?.also { newNodeType ->
            requireCanUseNodeTypeForNotation(newNodeType, notation)
        } ?: component.nodeType

        mdFileLinkValidator.validate(request.attrs)
        val updated = componentsRepository.save(
            component.copy(
                name = request.name ?: component.name,
                attrs = request.attrs ?: component.attrs,
                version = request.version ?: component.version,
                owner = owner,
                notation = notation,
                nodeType = nodeType
            )
        )
        return updated.toResponse(accessService)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComponent(@PathVariable id: UUID) {
        val component = componentsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $id not found")
            }
        accessService.requireCanEditComponent(component)
        componentsRepository.deleteById(id)
    }

    private fun parseTags(raw: String?): List<String> =
        raw
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    private fun List<String>.toJsonArray(): String =
        joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }

    private fun requireCanUseNodeTypeForNotation(
        nodeType: ru.kavader.arepos.model.NodeTypes,
        notation: ru.kavader.arepos.model.Notations
    ) {
        if (accessService.canUseNodeType(nodeType)) return
        val canEditNotation = accessService.canEditNotation(notation)
        if (canEditNotation && nodeType.owner.id == notation.owner.id) return
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
    }
}

