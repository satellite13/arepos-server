package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.*
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ACCESS_DENIED
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.*
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/diagrams")
@Tag(name = "Diagrams", description = "Diagram CRUD, previews and share-link endpoints")
class DiagramsController(
    private val diagramsRepository: DiagramsRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val notationsRepository: NotationsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val svgPreviewSecurityValidator: SvgPreviewSecurityValidator,
    private val diagramSvgStorage: DiagramSvgStorage,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val modelMapper: ModelMapper,
    private val diagramLifecycleService: DiagramLifecycleService,
    private val diagramShareLinkService: DiagramShareLinkService,
    private val diagramInstancesMergeService: DiagramInstancesMergeService
) {
    @GetMapping
    @Operation(summary = "List diagrams")
    fun listDiagrams(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) nodeId: UUID?,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false, defaultValue = "true") includeAttrs: Boolean
    ): Page<DiagramResponse> {
        val normalizedName = name.trimmedOrNull()
        val entities = accessService.listPageWithAdminBypass(
            adminQuery = {
                diagramsRepository.findByFilters(
                    ownerId = ownerId,
                    modelId = modelId,
                    nodeId = nodeId,
                    notationId = notationId,
                    name = normalizedName.orEmpty(),
                    pageable = pageable
                )
            },
            userQuery = { currentUserId ->
                diagramsRepository.findAccessibleByFiltersForUser(
                    ownerId = ownerId,
                    modelId = modelId,
                    nodeId = nodeId,
                    notationId = notationId,
                    name = normalizedName,
                    currentUserId = currentUserId,
                    pageable = pageable
                )
            }
        ).applyMcpModelAllowlist(accessService, modelId) { it.model.id }
        return entities.map { modelMapper.toResponse(it, includeAttrs) }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get diagram by id")
    fun getDiagram(@PathVariable id: UUID): DiagramResponse =
        diagramsRepository.findById(id)
            .map {
                accessService.requireCanViewDiagram(it)
                modelMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create diagram")
    fun createDiagram(@RequestBody @Valid request: DiagramRequest): DiagramResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        accessService.requireCanEditModel(model)
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        accessService.requireCanReferenceNotationForModelDiagram(notation, model)
        val node = request.nodeId?.let {
            nodesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $it not found")
            }
        }?.also { newNode ->
            accessService.requireCanEditNode(newNode)
        }
        if (diagramsRepository.existsByModelAndNameAndVersion(model, request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Diagram with model '${request.modelId}', name '${request.name}' and version '${request.version}' already exists"
            )
        }

        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = diagramsRepository.save(
            Diagrams(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                version = request.version,
                owner = owner,
                deleted = false,
                model = model,
                notation = notation,
                node = node
            )
        )
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(model.id),
            ModelSyncChangeType.DIAGRAM_CREATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_CREATED.wireValue,
                    ModelSyncEventType.DIAGRAM_CREATED.entity,
                    requireNotNull(saved.id)
                )
            )
        )
        return modelMapper.toResponse(saved)
    }

    @PostMapping("/{id}/instances:merge")
    @Operation(summary = "Merge/upsert diagram canvas instances by modelNodeId / modelLinkId")
    fun mergeInstances(
        @PathVariable id: UUID,
        @RequestBody @Valid request: DiagramInstancesMergeRequest
    ): DiagramInstancesMergeResponse = diagramInstancesMergeService.merge(id, request)

    @PutMapping("/{id}")
    @Operation(summary = "Update diagram")
    fun updateDiagram(
        @PathVariable id: UUID,
        @RequestBody @Valid request: DiagramUpdateRequest
    ): DiagramResponse {
        val diagram = diagramsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }
        accessService.requireCanEditDiagram(diagram)
        diagramLifecycleService.requireLatestDiagramVersion(diagram, "updated")

        val (owner, model) = ModelBoundEntityUpdateSupport.resolveOwnerAndModel(
            requestOwnerId = request.ownerId,
            requestModelId = request.modelId,
            currentOwner = diagram.owner,
            currentModel = diagram.model,
            ownerResolutionService = ownerResolutionService,
            modelsRepository = modelsRepository,
            accessService = accessService
        )
        val notation = if (accessService.canViewAdminPanel()) {
            request.notationId?.let {
                notationsRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $it not found")
                }
            }?.also { newNotation ->
                accessService.requireCanViewNotation(newNotation)
            } ?: diagram.notation
        } else {
            request.notationId?.let {
                if (it != diagram.notation.id) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
                }
            }
            diagram.notation
        }
        val node = request.nodeId?.let {
            nodesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $it not found")
            }
        }?.also { newNode ->
            accessService.requireCanEditNode(newNode)
        } ?: diagram.node
        val newName = request.name ?: diagram.name
        val newVersion = request.version ?: diagram.version

        if (diagramsRepository.existsByModelAndNameAndVersionAndIdNot(model, newName, newVersion, id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Diagram with model '${model.id}', name '$newName' and version '$newVersion' already exists"
            )
        }

        mdFileLinkValidator.validate(request.attrs)
        diagram.name = newName
        diagram.attrs = request.attrs ?: diagram.attrs
        diagram.version = newVersion
        diagram.owner = owner
        diagram.model = model
        diagram.notation = notation
        diagram.node = node
        val updated = diagramsRepository.save(diagram)
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(model.id),
            ModelSyncChangeType.DIAGRAM_UPDATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_UPDATED.wireValue,
                    ModelSyncEventType.DIAGRAM_UPDATED.entity,
                    requireNotNull(updated.id)
                )
            )
        )
        return modelMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete diagram")
    fun deleteDiagram(@PathVariable id: UUID) {
        val diagram = diagramsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }
        accessService.requireCanEditDiagram(diagram)
        diagramLifecycleService.softDeleteDiagram(diagram)
    }

    @PutMapping("/{id}/svg")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Upload diagram preview SVG")
    fun uploadDiagramSvg(@PathVariable id: UUID, @RequestBody body: String) {
        val diagram = diagramsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }
        accessService.requireCanEditDiagram(diagram)
        svgPreviewSecurityValidator.validate(body)
        when (val writeResult = diagramSvgStorage.putSvg(id, body)) {
            DiagramSvgWriteResult.Written -> return
            DiagramSvgWriteResult.Unavailable ->
                throw ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Diagram preview storage is not available"
                )

            is DiagramSvgWriteResult.StorageError ->
                throw ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    writeResult.message ?: "Diagram preview storage failed to write SVG"
                )
        }
    }

    @PostMapping("/share-link")
    @Operation(summary = "Create or reuse public diagram preview link")
    fun createShareLink(@RequestBody @Valid request: DiagramShareLinkRequest): DiagramShareLinkResponse =
        diagramShareLinkService.createShareLink(request)

    @GetMapping("svg/public/{token}")
    @Operation(summary = "Get public diagram preview by share token")
    fun getDiagramSvgPublic(@PathVariable token: UUID): ResponseEntity<ByteArray> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType("image/svg+xml")
            set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"diagram-preview.svg\"")
            cacheControl = "public, max-age=300"
        }
        return ResponseEntity.ok().headers(headers).body(diagramShareLinkService.resolvePublicSvg(token))
    }

    @DeleteMapping("share-link/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke diagram preview share link")
    fun revokeShareLink(@PathVariable token: UUID) = diagramShareLinkService.revokeShareLink(token)

    @PostMapping("/{id}/baseline")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create baseline diagram version")
    fun createBaseline(@PathVariable id: UUID): DiagramResponse {
        val diagram = diagramsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }
        accessService.requireCanEditDiagram(diagram)
        return modelMapper.toResponse(diagramLifecycleService.createBaseline(diagram))
    }


}
