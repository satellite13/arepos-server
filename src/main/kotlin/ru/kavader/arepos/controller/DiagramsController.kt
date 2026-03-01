package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/diagrams")
class DiagramsController(
    private val diagramsRepository: DiagramsRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val notationsRepository: NotationsRepository,
    private val accessService: ResourceAccessService,
    private val mdFileLinkValidator: MdFileLinkValidator
) {

    @GetMapping
    fun listDiagrams(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) nodeId: UUID?,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<DiagramResponse> {
        if (!CurrentUser.isAdmin()) {
            val filtered = diagramsRepository.findAll(Pageable.unpaged()).content
                .asSequence()
                .filter { accessService.canViewDiagram(it) }
                .filter { ownerId == null || it.owner.id == ownerId }
                .filter { modelId == null || it.model.id == modelId }
                .filter { nodeId == null || it.node?.id == nodeId }
                .filter { notationId == null || it.notation.id == notationId }
                .filter { name == null || it.name.contains(name, ignoreCase = true) }
                .toList()
            return filtered.toPage(pageable).map { it.toResponse() }
        }

        return diagramsRepository.findByFilters(
            ownerId = ownerId,
            modelId = modelId,
            nodeId = nodeId,
            notationId = notationId,
            name = name.orEmpty(),
            pageable = pageable
        ).map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getDiagram(@PathVariable id: UUID): DiagramResponse =
        diagramsRepository.findById(id)
            .map {
                accessService.requireCanViewDiagram(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDiagram(@RequestBody request: DiagramRequest): DiagramResponse {
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
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        accessService.requireCanEditNotation(notation)
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
        return saved.toResponse()
    }

    @PutMapping("/{id}")
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

        val owner = if (CurrentUser.isAdmin()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: diagram.owner
        } else {
            diagram.owner
        }
        val model = request.modelId?.let {
            modelsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $it not found")
            }
        }?.also { newModel ->
            accessService.requireCanEditModel(newModel)
        } ?: diagram.model
        val notation = if (CurrentUser.isAdmin()) {
            request.notationId?.let {
                notationsRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $it not found")
                }
            }?.also { newNotation ->
                accessService.requireCanEditNotation(newNotation)
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
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
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
    }

    @PostMapping("/{id}/baseline")
    @ResponseStatus(HttpStatus.CREATED)
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
        return saved.toResponse()
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
        val aSemver = parseSemver(a.version)
        val bSemver = parseSemver(b.version)
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

    private fun parseSemver(version: String): Triple<Int, Int, Int>? {
        val parts = version.trim().split(".")
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        return Triple(major, minor, patch)
    }

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isEditorOrAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
    }

    private fun Diagrams.toResponse() = DiagramResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        ownerId = owner.id!!,
        modelId = model.id!!,
        nodeId = node?.id,
        notationId = notation.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class DiagramRequest(
    val name: String,
    val version: String,
    val ownerId: UUID? = null,
    val modelId: UUID,
    val nodeId: UUID? = null,
    val notationId: UUID,
    val attrs: String? = null
)

data class DiagramUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val modelId: UUID? = null,
    val nodeId: UUID? = null,
    val notationId: UUID? = null,
    val attrs: String? = null
)

data class DiagramResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val modelId: UUID,
    val nodeId: UUID?,
    val notationId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
