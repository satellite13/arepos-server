package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/models")
class ModelsController(
    private val modelsRepository: ModelsRepository,
    private val usersRepository: UsersRepository
) {

    @GetMapping
    fun listModels(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<ModelResponse> {
        val models = when {
            ownerId != null && name != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    modelsRepository.findByOwnerAndNameContainingIgnoreCase(owner, name, pageable)
                } else {
                    modelsRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    modelsRepository.findByOwner(owner, pageable)
                } else {
                    modelsRepository.findAll(pageable)
                }
            }
            name != null -> {
                modelsRepository.findByNameContainingIgnoreCase(name, pageable)
            }
            else -> {
                modelsRepository.findAll(pageable)
            }
        }
        return models.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getModel(@PathVariable id: UUID): ModelResponse =
        modelsRepository.findById(id)
            .map { it.toResponse() }
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
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val saved = modelsRepository.save(
            Models(
                name = request.name,
                createdAt = Instant.now(),
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
        val newName = request.name ?: model.name
        val newVersion = request.version ?: model.version
        if (modelsRepository.existsByNameAndVersionAndIdNot(newName, newVersion, id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Model with name '$newName' and version '$newVersion' already exists"
            )
        }
        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: model.owner

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
        if (!modelsRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
        }
        val deletedCount = modelsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Model $id not found")
        }
    }

    private fun Models.toResponse() = ModelResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        ownerId = owner.id!!,
        attrs = attrs
    )
}

data class ModelRequest(
    val name: String,
    val version: String,
    val ownerId: UUID,
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
    val attrs: String?
)

