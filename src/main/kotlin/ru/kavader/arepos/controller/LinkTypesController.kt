package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.notation.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/link-types")
class LinkTypesController(
    private val linkTypesRepository: LinkTypesRepository,
    private val usersRepository: UsersRepository,
    private val notationsRepository: NotationsRepository,
    private val relationsRepository: RelationsRepository,
    private val modelsRepository: ModelsRepository,
    private val linksRepository: LinksRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator
) {
    companion object {
        private val log = LoggerFactory.getLogger(LinkTypesController::class.java)
    }
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

    @GetMapping
    fun listLinkTypes(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) notationId: List<UUID>?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<LinkTypeResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            val normalizedName = name?.trim().orEmpty()
            if (notationId.isNullOrEmpty() && modelId == null) {
                return linkTypesRepository.findAccessibleForUser(
                    userId = currentUserId,
                    ownerId = ownerId,
                    name = normalizedName,
                    viewPermissions = viewPermissions,
                    pageable = pageable
                ).map { it.toResponse() }
            }

            val resolvedModel = modelId?.let { mid ->
                modelsRepository.findById(mid).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Model $mid not found")
                }.also { accessService.requireCanViewModel(it) }
            }
            val notationOwnerIds = mutableSetOf<UUID>()
            val notationLinkTypeIds = mutableSetOf<UUID>()
            notationId?.forEach { requestedNotationId ->
                val notation = notationsRepository.findById(requestedNotationId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $requestedNotationId not found")
                }
                val allowed = when (val m = resolvedModel) {
                    null -> accessService.canViewNotation(notation)
                    else -> accessService.canReferenceNotationForModelDiagram(notation, m)
                }
                if (!allowed) {
                    return@forEach
                }
                relationsRepository.findDistinctLinkTypeIdsByNotationId(requestedNotationId)
                    .forEach { notationLinkTypeIds.add(it) }
                notation.owner.id?.let { notationOwnerIds.add(it) }
            }
            val modelLinkTypeIds = resolvedModel?.let { model ->
                linksRepository.findDistinctLinkTypeIdsByModelId(model.id!!)
                    .toSet()
            } ?: emptySet()
            val accessible = linkTypesRepository.findAccessibleForUser(
                userId = currentUserId,
                ownerId = ownerId,
                name = normalizedName,
                viewPermissions = viewPermissions,
                pageable = Pageable.unpaged()
            ).content
            val ownerMatched = if (notationOwnerIds.isEmpty()) {
                emptyList()
            } else {
                linkTypesRepository.findByOwnerIdIn(notationOwnerIds)
            }
            val idMatchedIds = notationLinkTypeIds + modelLinkTypeIds
            val idMatched = if (idMatchedIds.isEmpty()) {
                emptyList()
            } else {
                linkTypesRepository.findByIdIn(idMatchedIds)
            }
            val filtered = (accessible + ownerMatched + idMatched)
                .asSequence()
                .distinctBy { it.id }
                .filter { ownerId == null || it.owner.id == ownerId }
                .filter { normalizedName.isEmpty() || it.name.contains(normalizedName, ignoreCase = true) }
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
                if (!accessService.canViewLinkType(it) && !accessService.canUseLinkType(it)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                }
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createLinkType(@RequestBody request: LinkTypeRequest): LinkTypeResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        mdFileLinkValidator.validate(request.attrs)
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
        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, linkType.owner)

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
            ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
        }
        accessService.requireCanEditLinkType(linkType)
        linkTypesRepository.deleteById(id)
    }

    private fun resolveReadableOwner(ownerId: UUID?): ru.kavader.arepos.model.Users? =
        ownerResolutionService.resolveReadableOwner(ownerId) { oid, uid ->
            linkTypesRepository.findAccessibleForUser(uid, oid, "", viewPermissions, Pageable.ofSize(1)).hasContent()
        }

    private fun LinkTypes.toResponse() = LinkTypeResponse(
        id = requireNotNull(id),
        name = name,
        ownerId = owner.id!!,
        accessPermission = accessService.linkTypeAccessPermission(this),
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
