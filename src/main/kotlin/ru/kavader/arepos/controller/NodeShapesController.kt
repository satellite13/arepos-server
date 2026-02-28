package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/node-shapes")
class NodeShapesController(
    private val nodeShapesRepository: NodeShapesRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {

    @GetMapping
    fun list(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeShapeResponse> {
        accessService.currentUserId() // require authenticated
        val page = when {
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $ownerId not found")
                }
                nodeShapesRepository.findByOwner(owner, pageable)
            }
            else -> nodeShapesRepository.findAll(pageable)
        }
        return page.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): NodeShapeResponse {
        accessService.currentUserId() // require authenticated
        val shape = nodeShapesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
        return shape.toResponse()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: NodeShapeRequest): NodeShapeResponse {
        val currentUserId = accessService.currentUserId()
        val owner = usersRepository.findById(currentUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $currentUserId not found")
        }
        val now = Instant.now()
        val saved = nodeShapesRepository.save(
            NodeShapes(
                name = request.name,
                owner = owner,
                outline = request.outline,
                contentArea = request.contentArea,
                createdAt = now,
                updatedAt = now
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: NodeShapeUpdateRequest
    ): NodeShapeResponse {
        val shape = nodeShapesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
        accessService.requireCanEditNodeShape(shape)
        val updated = nodeShapesRepository.save(
            shape.copy(
                name = request.name ?: shape.name,
                outline = request.outline ?: shape.outline,
                contentArea = request.contentArea ?: shape.contentArea,
                updatedAt = Instant.now()
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        val shape = nodeShapesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
        accessService.requireCanEditNodeShape(shape)
        nodeShapesRepository.deleteById(id)
    }

    private fun NodeShapes.toResponse() = NodeShapeResponse(
        id = requireNotNull(id),
        name = name,
        ownerId = owner.id!!,
        outline = outline,
        contentArea = contentArea,
        createdAt = createdAt,
        updatedAt = updatedAt,
        canEdit = accessService.canEditNodeShape(this)
    )
}

data class NodeShapeRequest(
    val name: String,
    val outline: String? = null,
    val contentArea: String? = null
)

data class NodeShapeUpdateRequest(
    val name: String? = null,
    val outline: String? = null,
    val contentArea: String? = null
)

data class NodeShapeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val outline: String?,
    val contentArea: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val canEdit: Boolean
)
