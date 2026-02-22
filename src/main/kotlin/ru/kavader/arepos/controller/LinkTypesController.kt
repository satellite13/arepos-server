package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/link-types")
class LinkTypesController(
    private val linkTypesRepository: LinkTypesRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {
    companion object {
        private val log = LoggerFactory.getLogger(LinkTypesController::class.java)
    }

    @GetMapping
    fun listLinkTypes(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<LinkTypeResponse> {
        if (!CurrentUser.isAdmin()) {
            val filtered = linkTypesRepository.findAll(Pageable.unpaged()).content
                .asSequence()
                .filter { accessService.canEditLinkType(it) }
                .filter { ownerId == null || it.owner.id == ownerId }
                .filter { name == null || it.name.contains(name, ignoreCase = true) }
                .toList()
            return filtered.toPage(pageable).map { it.toResponse() }
        }

        val effectiveOwner = resolveReadableOwner(ownerId)
        val linkTypes = when {
            effectiveOwner != null && name != null ->
                linkTypesRepository.findByOwnerAndNameContainingIgnoreCase(effectiveOwner, name, pageable)
            effectiveOwner != null ->
                linkTypesRepository.findByOwner(effectiveOwner, pageable)
            name != null ->
                linkTypesRepository.findByNameContainingIgnoreCase(name, pageable)
            else ->
                linkTypesRepository.findAll(pageable)
        }
        return linkTypes.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getLinkType(@PathVariable id: UUID): LinkTypeResponse =
        linkTypesRepository.findById(id)
            .map {
                accessService.requireCanEditLinkType(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createLinkType(@RequestBody request: LinkTypeRequest): LinkTypeResponse {
        val resolvedOwnerId = request.ownerId ?: CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")
        log.info(
            "createLinkType request: currentUserId={}, role={}, requestOwnerId={}, resolvedOwnerId={}",
            CurrentUser.getId(),
            CurrentUser.getRole(),
            request.ownerId,
            resolvedOwnerId
        )
        val currentUserId = accessService.currentUserId()
        if (!CurrentUser.isAdmin() && resolvedOwnerId != currentUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
        val now = Instant.now()
        val saved = linkTypesRepository.save(
            LinkTypes(
                name = request.name,
                createdAt = now,
                updatedAt = now,
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
        accessService.requireCanEditLinkType(linkType)
        val owner = request.ownerId?.let {
            val currentUserId = accessService.currentUserId()
            if (!CurrentUser.isAdmin() && currentUserId != linkType.owner.id) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            }
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
        val linkType = linkTypesRepository.findById(id).orElseThrow {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
        }
        accessService.requireCanEditLinkType(linkType)
        linkTypesRepository.deleteById(id)
    }

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isEditorOrAdmin()) {
            log.warn(
                "LinkTypes access denied: currentUserId={}, role={}, ownerId={}",
                currentUserId,
                CurrentUser.getRole(),
                ownerId
            )
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
            val hasSharedFromOwner = linkTypesRepository.findAll(Pageable.unpaged()).content.any {
                it.owner.id == ownerId && accessService.canEditLinkType(it)
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

    private fun LinkTypes.toResponse() = LinkTypeResponse(
        id = requireNotNull(id),
        name = name,
        ownerId = owner.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class LinkTypeRequest(
    val name: String,
    val ownerId: UUID? = null,
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
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
