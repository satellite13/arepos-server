package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notations")
class NotationsController(
    private val notationsRepository: NotationsRepository,
    private val usersRepository: UsersRepository
) {

    @GetMapping
    fun listNotations(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NotationResponse> {
        val notations = when {
            ownerId != null && name != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    notationsRepository.findByOwnerAndNameContainingIgnoreCase(owner, name, pageable)
                } else {
                    notationsRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    notationsRepository.findByOwner(owner, pageable)
                } else {
                    notationsRepository.findAll(pageable)
                }
            }
            name != null -> {
                notationsRepository.findByNameContainingIgnoreCase(name, pageable)
            }
            else -> {
                notationsRepository.findAll(pageable)
            }
        }
        return notations.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getNotation(@PathVariable id: UUID): NotationResponse =
        notationsRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNotation(@RequestBody request: NotationRequest): NotationResponse {
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val saved = notationsRepository.save(
            Notations(
                name = request.name,
                version = request.version,
                owner = owner,
                attrs = request.attrs,
                createdAt = Instant.now()
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateNotation(
        @PathVariable id: UUID,
        @RequestBody request: NotationUpdateRequest
    ): NotationResponse {
        val notation = notationsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }
        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: notation.owner

        val updated = notationsRepository.save(
            notation.copy(
                name = request.name ?: notation.name,
                version = request.version ?: notation.version,
                attrs = request.attrs ?: notation.attrs,
                owner = owner
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNotation(@PathVariable id: UUID) {
        if (!notationsRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
        }
        notationsRepository.deleteById(id)
    }

    private fun Notations.toResponse() = NotationResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        ownerId = owner.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class NotationRequest(
    val name: String,
    val version: String,
    val ownerId: UUID,
    val attrs: String? = null
)

data class NotationUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NotationResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

