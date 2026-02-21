package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/components")
class ComponentsController(
    private val componentsRepository: ComponentsRepository,
    private val usersRepository: UsersRepository,
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository
) {

    @GetMapping
    fun listComponents(
        pageable: Pageable,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<ComponentResponse> {
        val components = when {
            notationId != null && name != null -> {
                val notation = notationsRepository.findById(notationId).orElse(null)
                if (notation != null) {
                    componentsRepository.findByNotationAndNameContainingIgnoreCase(notation, name, pageable)
                } else {
                    componentsRepository.findAll(pageable)
                }
            }
            notationId != null -> {
                val notation = notationsRepository.findById(notationId).orElse(null)
                if (notation != null) {
                    componentsRepository.findByNotation(notation, pageable)
                } else {
                    componentsRepository.findAll(pageable)
                }
            }
            ownerId != null && name != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    componentsRepository.findByOwnerAndNameContainingIgnoreCase(owner, name, pageable)
                } else {
                    componentsRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    componentsRepository.findByOwner(owner, pageable)
                } else {
                    componentsRepository.findAll(pageable)
                }
            }
            name != null -> {
                componentsRepository.findByNameContainingIgnoreCase(name, pageable)
            }
            else -> {
                componentsRepository.findAll(pageable)
            }
        }
        return components.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getComponent(@PathVariable id: UUID): ComponentResponse =
        componentsRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createComponent(@RequestBody request: ComponentRequest): ComponentResponse {
        val resolvedOwnerId = request.ownerId ?: CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        val nodeType = nodeTypesRepository.findById(request.nodeTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType ${request.nodeTypeId} not found")
            }
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
        checkOwnerOrRole(component.owner.id!!)

        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: component.owner
        val notation = request.notationId?.let {
            notationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $it not found")
            }
        } ?: component.notation
        val nodeType = request.nodeTypeId?.let {
            nodeTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $it not found")
            }
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
        checkOwnerOrRole(component.owner.id!!)
        componentsRepository.deleteById(id)
    }

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isEditorOrAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
    }

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
