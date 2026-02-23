package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/components")
class ComponentsController(
    private val componentsRepository: ComponentsRepository,
    private val usersRepository: UsersRepository,
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val diagramsRepository: DiagramsRepository,
    private val accessService: ResourceAccessService
) {

    @GetMapping
    fun listComponents(
        pageable: Pageable,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) tagsAll: String?
    ): Page<ComponentResponse> {
        val normalizedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val tags = parseTags(tagsAll)
        val tagsJson = if (tags.isEmpty()) null else tags.toJsonArray()

        if (!CurrentUser.isAdmin()) {
            val accessibleNotationIds = diagramsRepository.findAll(Pageable.unpaged()).content
                .asSequence()
                .filter { accessService.canViewDiagram(it) }
                .mapNotNull { it.notation.id }
                .toSet()
            val filtered = componentsRepository
                .findByFilters(notationId, ownerId, normalizedName, tagsJson, Pageable.unpaged())
                .content
                .asSequence()
                .filter {
                    accessService.canViewComponent(it) || accessibleNotationIds.contains(it.notation.id)
                }
                .toList()
            return filtered.toPage(pageable).map { it.toResponse() }
        }

        val components = componentsRepository.findByFilters(
            notationId = notationId,
            ownerId = ownerId,
            name = normalizedName,
            tagsJson = tagsJson,
            pageable = pageable
        )
        return components.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getComponent(@PathVariable id: UUID): ComponentResponse =
        componentsRepository.findById(id)
            .map {
                accessService.requireCanViewComponent(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createComponent(@RequestBody request: ComponentRequest): ComponentResponse {
        val currentUserId = accessService.currentUserId()
        val resolvedOwnerId = if (CurrentUser.isAdmin()) {
            request.ownerId ?: currentUserId
        } else {
            currentUserId
        }
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
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
        return saved.toResponse()
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

        val owner = if (CurrentUser.isAdmin()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: component.owner
        } else {
            component.owner
        }
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
        return updated.toResponse()
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

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isEditorOrAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
    }

    private fun getCurrentUser() = CurrentUser.getId()?.let {
        usersRepository.findById(it).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $it not found")
        }
    } ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

    private fun Components.toResponse() = ComponentResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        notationId = notation.id!!,
        ownerId = owner.id!!,
        nodeTypeId = nodeType.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

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
        if (CurrentUser.isAdmin()) return
        if (accessService.canEditNotation(notation) && nodeType.owner.id == notation.owner.id) return
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
    }
}

data class ComponentRequest(
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID,
    val attrs: String? = null
)

data class ComponentUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val notationId: UUID? = null,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val attrs: String? = null
)

data class ComponentResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID,
    val nodeTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
