package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/relations")
class RelationsController(
    private val relationsRepository: RelationsRepository,
    private val usersRepository: UsersRepository,
    private val notationsRepository: NotationsRepository,
    private val linkTypesRepository: LinkTypesRepository
) {

    @GetMapping
    fun listRelations(
        pageable: Pageable,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<RelationResponse> {
        val relations = when {
            notationId != null && name != null -> {
                val notation = notationsRepository.findById(notationId).orElse(null)
                if (notation != null) {
                    relationsRepository.findByNotationAndNameContainingIgnoreCase(notation, name, pageable)
                } else {
                    relationsRepository.findAll(pageable)
                }
            }
            notationId != null -> {
                val notation = notationsRepository.findById(notationId).orElse(null)
                if (notation != null) {
                    relationsRepository.findByNotation(notation, pageable)
                } else {
                    relationsRepository.findAll(pageable)
                }
            }
            ownerId != null && name != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    relationsRepository.findByOwnerAndNameContainingIgnoreCase(owner, name, pageable)
                } else {
                    relationsRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    relationsRepository.findByOwner(owner, pageable)
                } else {
                    relationsRepository.findAll(pageable)
                }
            }
            name != null -> {
                relationsRepository.findByNameContainingIgnoreCase(name, pageable)
            }
            else -> {
                relationsRepository.findAll(pageable)
            }
        }
        return relations.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getRelation(@PathVariable id: UUID): RelationResponse =
        relationsRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRelation(@RequestBody request: RelationRequest): RelationResponse {
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        val linkType = linkTypesRepository.findById(request.linkTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType ${request.linkTypeId} not found")
            }
        val now = Instant.now()
        val saved = relationsRepository.save(
            Relations(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                version = request.version,
                owner = owner,
                notation = notation,
                linkType = linkType
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateRelation(
        @PathVariable id: UUID,
        @RequestBody request: RelationUpdateRequest
    ): RelationResponse {
        val relation = relationsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $id not found")
            }
        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: relation.owner
        val notation = request.notationId?.let {
            notationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $it not found")
            }
        } ?: relation.notation
        val linkType = request.linkTypeId?.let {
            linkTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $it not found")
            }
        } ?: relation.linkType

        val updated = relationsRepository.save(
            relation.copy(
                name = request.name ?: relation.name,
                attrs = request.attrs ?: relation.attrs,
                version = request.version ?: relation.version,
                owner = owner,
                notation = notation,
                linkType = linkType
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRelation(@PathVariable id: UUID) {
        if (!relationsRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Relation $id not found")
        }
        relationsRepository.deleteById(id)
    }

    private fun Relations.toResponse() = RelationResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        notationId = notation.id!!,
        ownerId = owner.id!!,
        linkTypeId = linkType.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class RelationRequest(
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String? = null
)

data class RelationUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val notationId: UUID? = null,
    val ownerId: UUID? = null,
    val linkTypeId: UUID? = null,
    val attrs: String? = null
)

data class RelationResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

