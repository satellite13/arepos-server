package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.dto.notation.NodeTypeRequest
import ru.kavader.arepos.dto.notation.NodeTypeResponse
import ru.kavader.arepos.dto.notation.NodeTypeUpdateRequest
import ru.kavader.arepos.mapper.NotationMapper
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.security.ACCESS_DENIED
import ru.kavader.arepos.security.ADMIN_ONLY
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.CatalogLifecycleService
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.SystemRootNodeTypeService
import ru.kavader.arepos.service.TypeCatalogListService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/node-types")
@Tag(name = "Node Types", description = "Node type catalog endpoints")
class NodeTypesController(
    private val nodeTypesRepository: NodeTypesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val typeCatalogListService: TypeCatalogListService,
    private val catalogLifecycleService: CatalogLifecycleService,
    private val systemRootNodeTypeService: SystemRootNodeTypeService,
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

    @GetMapping("/deleted")
    @Operation(summary = "List soft-deleted node types (admin)")
    fun listDeletedNodeTypes(pageable: Pageable): ListResponse<NodeTypeResponse> {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ADMIN_ONLY)
        }
        return mapNodeTypesPage(nodeTypesRepository.findByDeletedTrue(pageable)).toListResponse()
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get node type by id")
    fun getNodeType(@PathVariable id: UUID): NodeTypeResponse =
        nodeTypesRepository.findById(id)
            .map {
                if (!accessService.canViewNodeType(it) && !accessService.canUseNodeType(it)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, ACCESS_DENIED)
                }
                notationMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create node type")
    fun createNodeType(@RequestBody @Valid request: NodeTypeRequest): NodeTypeResponse {
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
        @RequestBody @Valid request: NodeTypeUpdateRequest
    ): NodeTypeResponse {
        val nodeType = nodeTypesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }
        accessService.requireCanEditNodeType(nodeType)
        systemRootNodeTypeService.assertMutable(nodeType)
        return CatalogTypeWriteSupport.persistUpdate(
            entity = nodeType,
            request = request,
            ownerResolutionService = ownerResolutionService,
            mdFileLinkValidator = mdFileLinkValidator,
            save = nodeTypesRepository::save,
            toResponse = notationMapper::toResponse
        )
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete node type")
    fun deleteNodeType(@PathVariable id: UUID) {
        val nodeType = nodeTypesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
        }
        accessService.requireCanEditNodeType(nodeType)
        catalogLifecycleService.softDeleteNodeType(id)
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete node type (admin)")
    fun permanentDeleteNodeType(@PathVariable id: UUID) {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ADMIN_ONLY)
        }
        val nodeType = nodeTypesRepository.findByIdIncludingDeleted(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
        }
        catalogLifecycleService.permanentDeleteNodeType(nodeType)
    }

    private fun mapNodeTypesPage(page: Page<NodeTypes>): Page<NodeTypeResponse> {
        val visible = page.content.filterNot(systemRootNodeTypeService::isProtectedSystemDirectory)
        val filteredPage =
            if (visible.size == page.content.size) {
                page
            } else {
                val removed = page.content.size - visible.size
                PageImpl(
                    visible,
                    page.pageable,
                    (page.totalElements - removed).coerceAtLeast(0)
                )
            }
        return filteredPage.mapWithPermissions(
            loadPermissions = accessService::nodeTypeAccessPermissions,
            idOf = NodeTypes::id,
            transform = notationMapper::toResponse
        )
    }
}
