package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.notation.NodeShapeRequest
import ru.kavader.arepos.dto.notation.NodeShapeResponse
import ru.kavader.arepos.dto.notation.NodeShapeUpdateRequest
import ru.kavader.arepos.dto.notation.NotationMapper
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/node-shapes")
@Tag(name = "Node Shapes", description = "Node shape management endpoints")
class NodeShapesController(
    private val nodeShapesRepository: NodeShapesRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val notationMapper: NotationMapper
) {

    @GetMapping
    @Operation(summary = "List node shapes")
    fun list(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeShapeResponse> {
        accessService.currentUserId() // require authenticated
        val allShapes = when {
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $ownerId not found")
                }
                nodeShapesRepository.findByOwner(owner, Pageable.unpaged()).content
            }

            else -> nodeShapesRepository.findAll(Pageable.unpaged()).content
        }
        val normalizedName = name?.trim().orEmpty()
        val visibleShapes = accessService.filterViewableNodeShapes(allShapes)
            .asSequence()
            .filter { normalizedName.isEmpty() || it.name.contains(normalizedName, ignoreCase = true) }
            .toList()
        return mapNodeShapesPage(visibleShapes.toPage(pageable))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get node shape by id")
    fun get(@PathVariable id: UUID): NodeShapeResponse {
        accessService.currentUserId() // require authenticated
        val shape = nodeShapesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
        accessService.requireCanViewNodeShape(shape)
        return notationMapper.toResponse(shape)
    }

    @PostMapping
    @Operation(summary = "Create node shape")
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
                attrs = request.attrs,
                createdAt = now,
                updatedAt = now
            )
        )
        return notationMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update node shape")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: NodeShapeUpdateRequest
    ): NodeShapeResponse {
        val shape = nodeShapesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
        accessService.requireCanEditNodeShape(shape)
        shape.name = request.name ?: shape.name
        shape.outline = request.outline ?: shape.outline
        shape.contentArea = request.contentArea ?: shape.contentArea
        shape.attrs = request.attrs ?: shape.attrs
        shape.updatedAt = Instant.now()
        val updated = nodeShapesRepository.save(shape)
        return notationMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete node shape")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        val shape = nodeShapesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
        accessService.requireCanEditNodeShape(shape)
        nodeShapesRepository.deleteById(id)
    }

    private fun mapNodeShapesPage(page: Page<NodeShapes>): Page<NodeShapeResponse> {
        val permissions = accessService.nodeShapeAccessPermissions(page.content)
        val mapped = page.content.map { shape ->
            val shapeId = requireNotNull(shape.id)
            notationMapper.toResponse(shape, permissions[shapeId])
        }
        return PageImpl(mapped, page.pageable, page.totalElements)
    }

}
