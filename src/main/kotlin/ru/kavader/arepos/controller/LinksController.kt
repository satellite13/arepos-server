package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.LinkRequest
import ru.kavader.arepos.dto.model.LinkResponse
import ru.kavader.arepos.dto.model.LinkUpdateRequest
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.DiagramCanvasInstancesCleanupService
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.ModelSyncBroadcaster
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/links")
@Tag(name = "Links", description = "Model links management endpoints")
class LinksController(
    private val linksRepository: LinksRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val diagramCanvasInstancesCleanupService: DiagramCanvasInstancesCleanupService,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val typeUsageAuthorization: TypeUsageAuthorization,
    private val modelMapper: ModelMapper,
    private val linkEnsureService: ru.kavader.arepos.service.LinkEnsureService
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
            )
                .applyMcpModelAllowlist(accessService, modelId) { it.model.id }
                .map { modelMapper.toResponse(it) }
        }

        val links = when {
            modelId != null && ownerId != null -> {
                val model = modelsRepository.findById(modelId).orElse(null)
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (model != null && owner != null) {
                    linksRepository.findByModelAndOwnerOrderByIdAsc(model, owner, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }

            modelId != null -> {
                val model = modelsRepository.findById(modelId).orElse(null)
                if (model != null) {
                    linksRepository.findByModelOrderByIdAsc(model, pageable)
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
    fun createLink(@RequestBody @Valid request: LinkRequest): LinkResponse =
        linkEnsureService.createLink(request)

    @PostMapping("/ensure")
    @Operation(summary = "Find or create link by model/source/target/linkType (after notation resolve)")
    fun ensureLink(@RequestBody @Valid request: LinkRequest): ru.kavader.arepos.dto.model.EnsureLinkResponse =
        linkEnsureService.ensureLink(request)

    @PutMapping("/{id}")
    @Operation(summary = "Update link")
    fun updateLink(
        @PathVariable id: UUID,
        @RequestBody @Valid request: LinkUpdateRequest
    ): LinkResponse {
        val link = linksRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }
        accessService.requireCanEditLink(link)

        val (owner, model) = ModelBoundEntityUpdateSupport.resolveOwnerAndModel(
            requestOwnerId = request.ownerId,
            requestModelId = request.modelId,
            currentOwner = link.owner,
            currentModel = link.model,
            ownerResolutionService = ownerResolutionService,
            modelsRepository = modelsRepository,
            accessService = accessService
        )
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
            typeUsageAuthorization.requireCanUseLinkTypeForModel(newLinkType, model)
        } ?: link.linkType

        mdFileLinkValidator.validate(request.attrs)
        link.source = source
        link.target = target
        link.attrs = request.attrs ?: link.attrs
        link.owner = owner
        link.linkType = linkType
        link.model = model
        val updated = linksRepository.save(link)
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
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.LINK_DELETED.wireValue,
                    ModelSyncEventType.LINK_DELETED.entity,
                    id
                )
            )
        )
    }


}

