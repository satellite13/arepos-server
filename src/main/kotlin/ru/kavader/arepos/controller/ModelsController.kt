package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
class ModelsController(
    private val modelsRepository: ModelsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {

    @GetMapping
    fun listModels(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<ModelResponse> {
        if (!CurrentUser.isAdmin()) {
            val filtered = modelsRepository.findAll(Pageable.unpaged()).content
                .asSequence()
                .filter { accessService.canViewModel(it) }
                .filter { ownerId == null || it.owner.id == ownerId }
                .filter { name == null || it.name.contains(name, ignoreCase = true) }
                .toList()
            return filtered.toPage(pageable).map { it.toResponse() }
        }

        val effectiveOwner = resolveReadableOwner(ownerId)
        val models = when {
            effectiveOwner != null && name != null ->
                modelsRepository.findByOwnerAndNameContainingIgnoreCase(effectiveOwner, name, pageable)
            effectiveOwner != null ->
                modelsRepository.findByOwner(effectiveOwner, pageable)
            name != null ->
                modelsRepository.findByNameContainingIgnoreCase(name, pageable)
            else ->
                modelsRepository.findAll(pageable)
        }
        return models.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getModel(@PathVariable id: UUID): ModelResponse =
        modelsRepository.findById(id)
            .map {
                accessService.requireCanViewModel(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createModel(@RequestBody request: ModelRequest): ModelResponse {
        if (modelsRepository.existsByNameAndVersion(request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '${request.name}' and version '${request.version}' already exists"
            )
        }
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
        val now = Instant.now()
        val saved = modelsRepository.save(
            Models(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                version = request.version,
                owner = owner,
                deleted = false
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateModel(
        @PathVariable id: UUID,
        @RequestBody request: ModelUpdateRequest
    ): ModelResponse {
        val model = modelsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        accessService.requireCanEditModel(model)

        val newName = request.name ?: model.name
        val newVersion = request.version ?: model.version
        if (modelsRepository.existsByNameAndVersionAndIdNot(newName, newVersion, id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '$newName' and version '$newVersion' already exists"
            )
        }
        val owner = if (CurrentUser.isAdmin()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: model.owner
        } else {
            model.owner
        }

        val updated = modelsRepository.save(
            model.copy(
                name = newName,
                attrs = request.attrs ?: model.attrs,
                version = newVersion,
                owner = owner
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun deleteModel(@PathVariable id: UUID) {
        val model = modelsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
            }
        accessService.requireCanEditModel(model)
        val deletedCount = modelsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
        }
    }

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
    }

    private fun resolveReadableOwner(ownerId: UUID?): ru.kavader.arepos.model.Users? {
        val currentUserId = CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        if (CurrentUser.isAdmin()) {
            return ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            }
        }

        if (ownerId != null && ownerId != currentUserId) {
            // Non-admin users can filter by owner only if they have shared access from that owner.
            val hasSharedFromOwner = modelsRepository.findAll(Pageable.unpaged()).content.any {
                it.owner.id == ownerId && accessService.canViewModel(it)
            }
            if (!hasSharedFromOwner) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            }
            return null
        }

        return usersRepository.findById(currentUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $currentUserId not found")
        }
    }

    private fun Models.toResponse() = ModelResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        ownerId = owner.id!!,
        accessPermission = accessService.modelAccessPermission(this),
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class ModelRequest(
    val name: String,
    val version: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class ModelUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class ModelResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
