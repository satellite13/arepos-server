package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.dto.notation.NodeShapeRequest
import ru.kavader.arepos.dto.notation.NodeShapeResponse
import ru.kavader.arepos.dto.notation.NodeShapeUpdateRequest
import ru.kavader.arepos.mapper.NotationMapper
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ADMIN_ONLY
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.CatalogLifecycleService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/node-shapes")
@Tag(name = "Node Shapes", description = "Node shape management endpoints")
class NodeShapesController(
    private val nodeShapesRepository: NodeShapesRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val catalogLifecycleService: CatalogLifecycleService,
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

    @GetMapping("/deleted")
    @Operation(summary = "List soft-deleted node shapes (admin)")
    fun listDeleted(pageable: Pageable): ListResponse<NodeShapeResponse> {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ADMIN_ONLY)
        }
        return mapNodeShapesPage(nodeShapesRepository.findByDeletedTrue(pageable)).toListResponse()
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
    fun create(@RequestBody @Valid request: NodeShapeRequest): NodeShapeResponse {
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
        @RequestBody @Valid request: NodeShapeUpdateRequest
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
    @Operation(summary = "Soft-delete node shape")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        val shape = nodeShapesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
        accessService.requireCanEditNodeShape(shape)
        catalogLifecycleService.softDeleteNodeShape(id)
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete node shape (admin)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun permanentDelete(@PathVariable id: UUID) {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ADMIN_ONLY)
        }
        val shape = nodeShapesRepository.findByIdIncludingDeleted(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeShape $id not found")
        }
        catalogLifecycleService.permanentDeleteNodeShape(shape)
    }

    private fun mapNodeShapesPage(page: Page<NodeShapes>): Page<NodeShapeResponse> =
        page.mapWithPermissions(
            loadPermissions = accessService::nodeShapeAccessPermissions,
            idOf = NodeShapes::id,
            transform = notationMapper::toResponse
        )

}
