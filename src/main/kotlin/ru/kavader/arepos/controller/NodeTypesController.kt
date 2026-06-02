package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.notation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.TypeCatalogListService
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/node-types")
@Tag(name = "Node Types", description = "Node type catalog endpoints")
class NodeTypesController(
    private val nodeTypesRepository: NodeTypesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val typeCatalogListService: TypeCatalogListService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val notationMapper: NotationMapper
) {

    @GetMapping
    @Operation(summary = "List node types")
    fun listNodeTypes(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) notationId: List<UUID>?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeTypeResponse> =
        mapNodeTypesPage(
            typeCatalogListService.listNodeTypes(pageable, ownerId, notationId, modelId, name)
        )

    @GetMapping("/{id}")
    @Operation(summary = "Get node type by id")
    fun getNodeType(@PathVariable id: UUID): NodeTypeResponse =
        nodeTypesRepository.findById(id)
            .map {
                if (!accessService.canViewNodeType(it) && !accessService.canUseNodeType(it)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                }
                notationMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create node type")
    fun createNodeType(@RequestBody request: NodeTypeRequest): NodeTypeResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = nodeTypesRepository.save(
            NodeTypes(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                owner = owner
            )
        )
        return notationMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update node type")
    fun updateNodeType(
        @PathVariable id: UUID,
        @RequestBody request: NodeTypeUpdateRequest
    ): NodeTypeResponse {
        val nodeType = nodeTypesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }
        accessService.requireCanEditNodeType(nodeType)
        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, nodeType.owner)

        val updated = nodeTypesRepository.save(
            nodeType.copy(
                name = request.name ?: nodeType.name,
                attrs = request.attrs ?: nodeType.attrs,
                owner = owner
            )
        )
        return notationMapper.toResponse(updated)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete node type")
    fun deleteNodeType(@PathVariable id: UUID) {
        val nodeType = nodeTypesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
        }
        accessService.requireCanEditNodeType(nodeType)
        nodeTypesRepository.deleteById(id)
    }

    private fun mapNodeTypesPage(page: Page<NodeTypes>): Page<NodeTypeResponse> {
        val permissions = accessService.nodeTypeAccessPermissions(page.content)
        val mapped = page.content.map { nodeType ->
            val nodeTypeId = requireNotNull(nodeType.id)
            notationMapper.toResponse(nodeType, permissions[nodeTypeId])
        }
        return PageImpl(mapped, page.pageable, page.totalElements)
    }
}
