package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.*
import ru.kavader.arepos.dto.model.ModelMapper
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
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
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.service.DiagramCanvasInstancesCleanupService
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.ModelDiagramTypeValidator
import ru.kavader.arepos.service.ModelSyncBroadcaster
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/links")
@Tag(name = "Links", description = "Model links management endpoints")
class LinksController(
    private val linksRepository: LinksRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val diagramsRepository: DiagramsRepository,
    private val relationsRepository: RelationsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val diagramCanvasInstancesCleanupService: DiagramCanvasInstancesCleanupService,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val typeValidator: ModelDiagramTypeValidator,
    private val modelMapper: ModelMapper
) {

    @GetMapping
    @Operation(summary = "List links")
    fun listLinks(
        pageable: Pageable,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) sourceId: UUID?,
        @RequestParam(required = false) targetId: UUID?,
        @RequestParam(required = false) linkTypeId: UUID?
    ): Page<LinkResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            return linksRepository.findAccessibleByFiltersForUser(
                modelId = modelId,
                ownerId = ownerId,
                sourceId = sourceId,
                targetId = targetId,
                linkTypeId = linkTypeId,
                currentUserId = currentUserId,
                pageable = pageable
            ).map { modelMapper.toResponse(it) }
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
        return links.map { modelMapper.toResponse(it) }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get link by id")
    fun getLink(@PathVariable id: UUID): LinkResponse =
        linksRepository.findById(id)
            .map {
                accessService.requireCanViewLink(it)
                modelMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }

    @PostMapping
    @Operation(summary = "Create link")
    @ResponseStatus(HttpStatus.CREATED)
    fun createLink(@RequestBody request: LinkRequest): LinkResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
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
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(model.id),
            ModelSyncChangeType.LINK_CREATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.LINK_CREATED.wireValue,
                    ModelSyncEventType.LINK_CREATED.entity,
                    requireNotNull(saved.id)
                )
            )
        )
        return modelMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update link")
    fun updateLink(
        @PathVariable id: UUID,
        @RequestBody request: LinkUpdateRequest
    ): LinkResponse {
        val link = linksRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }
        accessService.requireCanEditLink(link)

        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, link.owner)
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
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(model.id),
            ModelSyncChangeType.LINK_UPDATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.LINK_UPDATED.wireValue,
                    ModelSyncEventType.LINK_UPDATED.entity,
                    requireNotNull(updated.id)
                )
            )
        )
        return modelMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLink(@PathVariable id: UUID) {
        val link = linksRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }
        accessService.requireCanEditLink(link)
        val modelId = requireNotNull(link.model.id)
        linksRepository.deleteById(id)
        diagramCanvasInstancesCleanupService.removeDeletedModelEntitiesFromAllDiagrams(
            modelId,
            emptyList(),
            listOf(id),
            Instant.now()
        )
        modelSyncBroadcaster.broadcastModelChanged(
            modelId,
            ModelSyncChangeType.LINK_DELETE.wireValue,
            listOf(ModelSyncEntityEvent(ModelSyncEventType.LINK_DELETED.wireValue, ModelSyncEventType.LINK_DELETED.entity, id))
        )
    }


    private fun requireCanUseLinkTypeForModel(
        linkType: ru.kavader.arepos.model.LinkTypes,
        model: ru.kavader.arepos.model.Models
    ) {
        if (accessService.canUseLinkType(linkType)) return
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

    private fun isLinkTypeUsedInModelDiagramNotations(linkTypeId: UUID, model: ru.kavader.arepos.model.Models): Boolean =
        typeValidator.isLinkTypeUsedInModelDiagramNotations(linkTypeId, requireNotNull(model.id))
}

