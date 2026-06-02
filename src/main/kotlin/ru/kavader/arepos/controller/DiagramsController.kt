package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.*
import ru.kavader.arepos.dto.model.ModelMapper
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.model.DiagramPreviewLinks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.repository.DiagramPreviewLinksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.service.DiagramSvgReadResult
import ru.kavader.arepos.service.DiagramSvgStorage
import ru.kavader.arepos.service.DiagramSvgWriteResult
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.ModelSyncBroadcaster
import ru.kavader.arepos.service.SvgPreviewSecurityValidator
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import ru.kavader.arepos.util.VersionUtils
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/diagrams")
@Tag(name = "Diagrams", description = "Diagram CRUD, previews and share-link endpoints")
class DiagramsController(
    private val diagramsRepository: DiagramsRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val notationsRepository: NotationsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val svgPreviewSecurityValidator: SvgPreviewSecurityValidator,
    private val diagramSvgStorage: DiagramSvgStorage,
    private val diagramPreviewLinksRepository: DiagramPreviewLinksRepository,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val modelMapper: ModelMapper,
    @Value("\${arepos.public-url-base:}") private val publicUrlBase: String = ""
) {
    companion object {
        private const val PUBLIC_SVG_URL_PREFIX = "/api/v1/diagrams/svg/public/"
    }


    @GetMapping
    @Operation(summary = "List diagrams")
    fun listDiagrams(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) nodeId: UUID?,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<DiagramResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            return diagramsRepository.findAccessibleByFiltersForUser(
                ownerId = ownerId,
                modelId = modelId,
                nodeId = nodeId,
                notationId = notationId,
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                currentUserId = currentUserId,
                pageable = pageable
            ).map { modelMapper.toResponse(it) }
        }

        return diagramsRepository.findByFilters(
            ownerId = ownerId,
            modelId = modelId,
            nodeId = nodeId,
            notationId = notationId,
            name = name.orEmpty(),
            pageable = pageable
        ).map { modelMapper.toResponse(it) }
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

    @PutMapping("/{id}")
    @Operation(summary = "Update diagram")
    fun updateDiagram(
        @PathVariable id: UUID,
        @RequestBody request: DiagramUpdateRequest
    ): DiagramResponse {
        val diagram = diagramsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }
        accessService.requireCanEditDiagram(diagram)
        requireLatestDiagramVersion(diagram, "updated")

        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, diagram.owner)
        val model = request.modelId?.let {
            modelsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $it not found")
            }
        }?.also { newModel ->
            accessService.requireCanEditModel(newModel)
        } ?: diagram.model
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
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
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
        val updated = diagramsRepository.save(
            diagram.copy(
                name = newName,
                attrs = request.attrs ?: diagram.attrs,
                version = newVersion,
                owner = owner,
                model = model,
                notation = notation,
                node = node
            )
        )
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
    @Transactional
    @Operation(summary = "Soft-delete diagram")
    fun deleteDiagram(@PathVariable id: UUID) {
        val diagram = diagramsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }
        accessService.requireCanEditDiagram(diagram)
        val deletedCount = diagramsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
        }
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(diagram.model.id),
            ModelSyncChangeType.DIAGRAM_DELETE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_DELETED.wireValue,
                    ModelSyncEventType.DIAGRAM_DELETED.entity,
                    id
                )
            )
        )
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
    fun createShareLink(@RequestBody request: DiagramShareLinkRequest): DiagramShareLinkResponse {
        val currentUser = accessService.currentUserId()
        val user = usersRepository.findById(currentUser)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")
            }
        val link: DiagramPreviewLinks = when {
            request.diagramId != null -> {
                val diagram = diagramsRepository.findById(request.diagramId)
                    .orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram ${request.diagramId} not found")
                    }
                accessService.requireCanViewDiagram(diagram)
                val existing = diagramPreviewLinksRepository.findByDiagram(diagram)
                if (existing.isPresent) {
                    return DiagramShareLinkResponse(
                        url = buildPublicSvgUrl(existing.get().token!!),
                        token = existing.get().token!!
                    )
                }
                diagramPreviewLinksRepository.save(
                    DiagramPreviewLinks(
                        token = UUID.randomUUID(),
                        diagram = diagram,
                        model = null,
                        diagramName = null,
                        createdAt = Instant.now(),
                        createdBy = user
                    )
                )
            }
            request.modelId != null && request.diagramName != null && request.latest == true -> {
                val model = modelsRepository.findById(request.modelId)
                    .orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
                    }
                accessService.requireCanViewModel(model)
                val allByName = diagramsRepository.findByModel_IdAndNameAndDeletedFalse(model.id!!, request.diagramName)
                    .let { accessService.filterViewableDiagrams(it) }
                allByName.maxWithOrNull(::compareDiagramVersions)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No diagram named '${request.diagramName}' found")
                val existing = diagramPreviewLinksRepository.findByModelAndDiagramName(model, request.diagramName)
                if (existing.isPresent) {
                    return DiagramShareLinkResponse(
                        url = buildPublicSvgUrl(existing.get().token!!),
                        token = existing.get().token!!
                    )
                }
                diagramPreviewLinksRepository.save(
                    DiagramPreviewLinks(
                        token = UUID.randomUUID(),
                        diagram = null,
                        model = model,
                        diagramName = request.diagramName,
                        createdAt = Instant.now(),
                        createdBy = user
                    )
                )
            }
            else -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide either diagramId or (modelId, diagramName, latest: true)"
            )
        }
        return DiagramShareLinkResponse(
            url = buildPublicSvgUrl(link.token!!),
            token = link.token
        )
    }

    @GetMapping("svg/public/{token}")
    @Operation(summary = "Get public diagram preview by share token")
    fun getDiagramSvgPublic(@PathVariable token: UUID): ResponseEntity<ByteArray> {
        val link = diagramPreviewLinksRepository.findByToken(token).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found or expired")
        val diagramId: UUID = when {
            link.diagram != null -> link.diagram!!.id!!
            link.model != null && link.diagramName != null -> {
                val allByName = diagramsRepository.findByModel_IdAndNameAndDeletedFalse(link.model!!.id!!, link.diagramName!!)
                val latest = allByName.maxWithOrNull(::compareDiagramVersions)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram not found")
                latest.id!!
            }
            else -> throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid share link")
        }
        return when (val svgResult = diagramSvgStorage.getSvg(diagramId)) {
            is DiagramSvgReadResult.Found -> {
                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_OCTET_STREAM
                    set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diagram-preview.svg\"")
                    cacheControl = "public, max-age=300"
                }
                ResponseEntity.ok().headers(headers).body(svgResult.bytes)
            }
            DiagramSvgReadResult.NotFound ->
                throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Preview not found. The diagram owner can upload it in the editor."
                )
            is DiagramSvgReadResult.StorageError ->
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Preview storage is temporarily unavailable")
        }
    }

    @DeleteMapping("share-link/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @Operation(summary = "Revoke diagram preview share link")
    fun revokeShareLink(@PathVariable token: UUID) {
        val link = diagramPreviewLinksRepository.findByToken(token)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found")
            }
        val currentUserId = CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")
        val canRevoke = when {
            link.diagram != null -> accessService.canEditDiagram(link.diagram!!)
            link.model != null -> accessService.canEditModel(link.model!!)
            else -> false
        }
        if (!canRevoke) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot revoke this link")
        }
        diagramPreviewLinksRepository.delete(link)
    }

    @PostMapping("/{id}/baseline")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create baseline diagram version")
    fun createBaseline(@PathVariable id: UUID): DiagramResponse {
        val diagram = diagramsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }
        accessService.requireCanEditDiagram(diagram)
        requireLatestDiagramVersion(diagram, "used to create baseline")
        val newVersion = bumpMinorVersion(diagram.version)
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid diagram version '${diagram.version}'; expected semantic version (e.g. 1.2.3)"
            )
        if (diagramsRepository.existsByModelAndNameAndVersion(diagram.model, diagram.name, newVersion)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Diagram with name '${diagram.name}' and version '$newVersion' already exists"
            )
        }
        mdFileLinkValidator.validate(diagram.attrs)
        val now = Instant.now()
        val saved = diagramsRepository.save(
            Diagrams(
                name = diagram.name,
                createdAt = now,
                updatedAt = now,
                attrs = diagram.attrs,
                version = newVersion,
                owner = diagram.owner,
                deleted = false,
                model = diagram.model,
                notation = diagram.notation,
                node = diagram.node
            )
        )
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(diagram.model.id),
            ModelSyncChangeType.DIAGRAM_BASELINE.wireValue,
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

    /** Bumps minor version and resets patch: 1.2.3 -> 1.3.0 */
    private fun bumpMinorVersion(version: String): String? {
        val parts = version.trim().split(".")
        if (parts.size < 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return "$major.${minor + 1}.0"
    }

    private fun requireLatestDiagramVersion(diagram: Diagrams, action: String) {
        val modelId = diagram.model.id ?: return
        val allByName = diagramsRepository.findByModel_IdAndNameAndDeletedFalse(modelId, diagram.name)
        if (allByName.isEmpty()) return
        val latest = allByName.maxWithOrNull(::compareDiagramVersions) ?: return
        if (latest.id != diagram.id) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only latest diagram version can be $action. Latest version is '${latest.version}'."
            )
        }
    }

    private fun compareDiagramVersions(a: Diagrams, b: Diagrams): Int {
        val aSemver = VersionUtils.parseSemver(a.version)
        val bSemver = VersionUtils.parseSemver(b.version)
        if (aSemver != null && bSemver != null) {
            val majorCmp = aSemver.first.compareTo(bSemver.first)
            if (majorCmp != 0) return majorCmp
            val minorCmp = aSemver.second.compareTo(bSemver.second)
            if (minorCmp != 0) return minorCmp
            val patchCmp = aSemver.third.compareTo(bSemver.third)
            if (patchCmp != 0) return patchCmp
        }
        val aUpdated = a.updatedAt ?: a.createdAt ?: Instant.EPOCH
        val bUpdated = b.updatedAt ?: b.createdAt ?: Instant.EPOCH
        val timeCmp = aUpdated.compareTo(bUpdated)
        if (timeCmp != 0) return timeCmp
        val aId = a.id?.toString().orEmpty()
        val bId = b.id?.toString().orEmpty()
        return aId.compareTo(bId)
    }

    private fun buildPublicSvgUrl(token: UUID): String {
        val normalizedBase = publicUrlBase.trim().removeSuffix("/")
        val path = "$PUBLIC_SVG_URL_PREFIX$token"
        return if (normalizedBase.isBlank()) path else "$normalizedBase$path"
    }



}
