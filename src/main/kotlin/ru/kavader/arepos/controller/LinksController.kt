package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/links")
class LinksController(
    private val linksRepository: LinksRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val diagramsRepository: DiagramsRepository,
    private val relationsRepository: RelationsRepository,
    private val accessService: ResourceAccessService,
    private val mdFileLinkValidator: MdFileLinkValidator
) {

    @GetMapping
    fun listLinks(
        pageable: Pageable,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) sourceId: UUID?,
        @RequestParam(required = false) targetId: UUID?,
        @RequestParam(required = false) linkTypeId: UUID?
    ): Page<LinkResponse> {
        if (!CurrentUser.isAdmin()) {
            val currentUserId = accessService.currentUserId()
            return linksRepository.findAccessibleByFiltersForUser(
                modelId = modelId,
                ownerId = ownerId,
                sourceId = sourceId,
                targetId = targetId,
                linkTypeId = linkTypeId,
                currentUserId = currentUserId,
                pageable = pageable
            ).map { it.toResponse() }
        }

        val links = when {
            modelId != null && ownerId != null -> {
                val model = modelsRepository.findById(modelId).orElse(null)
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (model != null && owner != null) {
                    linksRepository.findByModelAndOwner(model, owner, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            modelId != null -> {
                val model = modelsRepository.findById(modelId).orElse(null)
                if (model != null) {
                    linksRepository.findByModel(model, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    linksRepository.findByOwner(owner, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            sourceId != null -> {
                val source = nodesRepository.findById(sourceId).orElse(null)
                if (source != null) {
                    linksRepository.findBySource(source, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            targetId != null -> {
                val target = nodesRepository.findById(targetId).orElse(null)
                if (target != null) {
                    linksRepository.findByTarget(target, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            linkTypeId != null -> {
                val linkType = linkTypesRepository.findById(linkTypeId).orElse(null)
                if (linkType != null) {
                    linksRepository.findByLinkType(linkType, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            else -> {
                linksRepository.findAll(pageable)
            }
        }
        return links.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getLink(@PathVariable id: UUID): LinkResponse =
        linksRepository.findById(id)
            .map {
                accessService.requireCanViewLink(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createLink(@RequestBody request: LinkRequest): LinkResponse {
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
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        accessService.requireCanEditModel(model)
        val source = nodesRepository.findById(request.sourceId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Source node ${request.sourceId} not found")
            }
        accessService.requireCanEditNode(source)
        val target = nodesRepository.findById(request.targetId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Target node ${request.targetId} not found")
            }
        accessService.requireCanEditNode(target)
        val linkType = linkTypesRepository.findById(request.linkTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType ${request.linkTypeId} not found")
            }
        requireCanUseLinkTypeForModel(linkType, model)
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = linksRepository.save(
            Links(
                stableId = request.stableId ?: UUID.randomUUID(),
                source = source,
                target = target,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                owner = owner,
                linkType = linkType,
                model = model
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateLink(
        @PathVariable id: UUID,
        @RequestBody request: LinkUpdateRequest
    ): LinkResponse {
        val link = linksRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }
        accessService.requireCanEditLink(link)

        val owner = if (CurrentUser.isAdmin()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: link.owner
        } else {
            link.owner
        }
        val model = request.modelId?.let {
            modelsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $it not found")
            }
        }?.also { newModel ->
            accessService.requireCanEditModel(newModel)
        } ?: link.model
        val source = request.sourceId?.let {
            nodesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Source node $it not found")
            }
        }?.also { newSource ->
            accessService.requireCanEditNode(newSource)
        } ?: link.source
        val target = request.targetId?.let {
            nodesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Target node $it not found")
            }
        }?.also { newTarget ->
            accessService.requireCanEditNode(newTarget)
        } ?: link.target
        val linkType = request.linkTypeId?.let {
            linkTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $it not found")
            }
        }?.also { newLinkType ->
            requireCanUseLinkTypeForModel(newLinkType, model)
        } ?: link.linkType

        mdFileLinkValidator.validate(request.attrs)
        val updated = linksRepository.save(
            link.copy(
                source = source,
                target = target,
                attrs = request.attrs ?: link.attrs,
                owner = owner,
                linkType = linkType,
                model = model
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLink(@PathVariable id: UUID) {
        val link = linksRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }
        accessService.requireCanEditLink(link)
        linksRepository.deleteById(id)
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

    private fun Links.toResponse() = LinkResponse(
        id = requireNotNull(id),
        stableId = stableId,
        sourceId = source.id!!,
        targetId = target.id!!,
        modelId = model.id!!,
        ownerId = owner.id!!,
        linkTypeId = linkType.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun requireCanUseLinkTypeForModel(
        linkType: ru.kavader.arepos.model.LinkTypes,
        model: ru.kavader.arepos.model.Models
    ) {
        if (accessService.canUseLinkType(linkType)) return
        if (CurrentUser.isAdmin()) return
        val canEditModel = accessService.canEditModel(model)
        if (canEditModel && linkType.owner.id == model.owner.id) return
        if (
            canEditModel &&
                isLinkTypeUsedInModelDiagramNotations(requireNotNull(linkType.id), model)
        ) {
            return
        }
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
    }

    private fun isLinkTypeUsedInModelDiagramNotations(
        linkTypeId: UUID,
        model: ru.kavader.arepos.model.Models
    ): Boolean {
        val notationIds = diagramsRepository.findDistinctNotationIdsByModelId(requireNotNull(model.id)).toSet()
        if (notationIds.isEmpty()) return false

        return relationsRepository.existsByLinkType_IdAndNotation_IdIn(linkTypeId, notationIds)
    }
}

data class LinkRequest(
    val sourceId: UUID,
    val targetId: UUID,
    val modelId: UUID,
    val ownerId: UUID? = null,
    val linkTypeId: UUID,
    val attrs: String? = null,
    val stableId: UUID? = null
)

data class LinkUpdateRequest(
    val sourceId: UUID? = null,
    val targetId: UUID? = null,
    val modelId: UUID? = null,
    val ownerId: UUID? = null,
    val linkTypeId: UUID? = null,
    val attrs: String? = null
)

data class LinkResponse(
    val id: UUID,
    val stableId: UUID,
    val sourceId: UUID,
    val targetId: UUID,
    val modelId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
