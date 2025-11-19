package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/link-types")
class LinkTypesController(
    private val linkTypesRepository: LinkTypesRepository,
    private val usersRepository: UsersRepository
) {

    @GetMapping
    fun listLinkTypes(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<LinkTypeResponse> {
        val linkTypes = when {
            ownerId != null && name != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    linkTypesRepository.findByOwnerAndNameContainingIgnoreCase(owner, name, pageable)
                } else {
                    linkTypesRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    linkTypesRepository.findByOwner(owner, pageable)
                } else {
                    linkTypesRepository.findAll(pageable)
                }
            }
            name != null -> {
                linkTypesRepository.findByNameContainingIgnoreCase(name, pageable)
            }
            else -> {
                linkTypesRepository.findAll(pageable)
            }
        }
        return linkTypes.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getLinkType(@PathVariable id: UUID): LinkTypeResponse =
        linkTypesRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createLinkType(@RequestBody request: LinkTypeRequest): LinkTypeResponse {
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val saved = linkTypesRepository.save(
            LinkTypes(
                name = request.name,
                createdAt = Instant.now(),
                attrs = request.attrs,
                owner = owner
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateLinkType(
        @PathVariable id: UUID,
        @RequestBody request: LinkTypeUpdateRequest
    ): LinkTypeResponse {
        val linkType = linkTypesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
            }
        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: linkType.owner

        val updated = linkTypesRepository.save(
            linkType.copy(
                name = request.name ?: linkType.name,
                attrs = request.attrs ?: linkType.attrs,
                owner = owner
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLinkType(@PathVariable id: UUID) {
        if (!linkTypesRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
        }
        linkTypesRepository.deleteById(id)
    }

    private fun LinkTypes.toResponse() = LinkTypeResponse(
        id = requireNotNull(id),
        name = name,
        ownerId = owner.id!!,
        attrs = attrs
    )
}

data class LinkTypeRequest(
    val name: String,
    val ownerId: UUID,
    val attrs: String? = null
)

data class LinkTypeUpdateRequest(
    val name: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class LinkTypeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val attrs: String?
)

