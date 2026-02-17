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
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/diagrams")
class DiagramsController(
    private val diagramsRepository: DiagramsRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val notationsRepository: NotationsRepository
) {

    @GetMapping
    fun listDiagrams(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<DiagramResponse> = diagramsRepository
        .findByFilters(
            ownerId = ownerId,
            modelId = modelId,
            notationId = notationId,
            name = name.orEmpty(),
            pageable = pageable
        )
        .map { it.toResponse() }

    @GetMapping("/{id}")
    fun getDiagram(@PathVariable id: UUID): DiagramResponse =
        diagramsRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDiagram(@RequestBody request: DiagramRequest): DiagramResponse {
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        if (diagramsRepository.existsByModelAndNameAndVersion(model, request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Diagram with model '${request.modelId}', name '${request.name}' and version '${request.version}' already exists"
            )
        }

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
                notation = notation
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

        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: diagram.owner
        val model = request.modelId?.let {
            modelsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $it not found")
            }
        } ?: diagram.model
        val notation = request.notationId?.let {
            notationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $it not found")
            }
        } ?: diagram.notation
        val newName = request.name ?: diagram.name
        val newVersion = request.version ?: diagram.version

        if (diagramsRepository.existsByModelAndNameAndVersionAndIdNot(model, newName, newVersion, id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Diagram with model '${model.id}', name '$newName' and version '$newVersion' already exists"
            )
        }

        val updated = diagramsRepository.save(
            diagram.copy(
                name = newName,
                attrs = request.attrs ?: diagram.attrs,
                version = newVersion,
                owner = owner,
                model = model,
                notation = notation
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun deleteDiagram(@PathVariable id: UUID) {
        if (!diagramsRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
        }
        val deletedCount = diagramsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $id not found")
        }
    }

    private fun Diagrams.toResponse() = DiagramResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        ownerId = owner.id!!,
        modelId = model.id!!,
        notationId = notation.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class DiagramRequest(
    val name: String,
    val version: String,
    val ownerId: UUID,
    val modelId: UUID,
    val notationId: UUID,
    val attrs: String? = null
)

data class DiagramUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val modelId: UUID? = null,
    val notationId: UUID? = null,
    val attrs: String? = null
)

data class DiagramResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val modelId: UUID,
    val notationId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
