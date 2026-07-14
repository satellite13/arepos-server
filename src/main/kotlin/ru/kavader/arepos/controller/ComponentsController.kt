package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.notation.ComponentRequest
import ru.kavader.arepos.dto.notation.ComponentResponse
import ru.kavader.arepos.dto.notation.ComponentUpdateRequest
import ru.kavader.arepos.mapper.NotationMapper
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/components")
@Tag(name = "Components", description = "Notation components management endpoints")
class ComponentsController(
    private val componentsRepository: ComponentsRepository,
    private val notationsRepository: NotationsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val typeUsageAuthorization: TypeUsageAuthorization,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val notationMapper: NotationMapper
) {

    @GetMapping
    @Operation(summary = "List components")
    fun listComponents(
        pageable: Pageable,
        @RequestParam(required = false) notationId: UUID?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) tagsAll: String?
    ): Page<ComponentResponse> =
        NotationBoundEntityListSupport.list(
            accessService = accessService,
            pageable = pageable,
            notationId = notationId,
            modelId = modelId,
            ownerId = ownerId,
            name = name,
            tagsAll = tagsAll,
            findAccessibleForUser = componentsRepository::findAccessibleByFiltersForUser,
            findByFilters = componentsRepository::findByFilters
        ).map { notationMapper.toResponse(it) }

    @GetMapping("/{id}")
    @Operation(summary = "Get component by id")
    fun getComponent(@PathVariable id: UUID): ComponentResponse =
        componentsRepository.findById(id)
            .map {
                accessService.requireCanViewComponent(it)
                notationMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $id not found")
            }

    @PostMapping
    @Operation(summary = "Create component")
    @ResponseStatus(HttpStatus.CREATED)
    fun createComponent(@RequestBody @Valid request: ComponentRequest): ComponentResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        val notation = notationsRepository.findById(request.notationId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation ${request.notationId} not found")
            }
        accessService.requireCanEditNotation(notation)
        val nodeType = nodeTypesRepository.findById(request.nodeTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType ${request.nodeTypeId} not found")
            }
        typeUsageAuthorization.requireCanUseNodeTypeForNotation(nodeType, notation)
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = componentsRepository.save(
            Components(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                version = request.version,
                notation = notation,
                owner = owner,
                nodeType = nodeType
            )
        )
        return notationMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update component")
    fun updateComponent(
        @PathVariable id: UUID,
        @RequestBody @Valid request: ComponentUpdateRequest
    ): ComponentResponse {
        val component = componentsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $id not found")
            }
        accessService.requireCanEditComponent(component)

        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, component.owner)
        val notation = request.notationId?.let {
            notationsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $it not found")
            }
        }?.also { newNotation ->
            accessService.requireCanEditNotation(newNotation)
        } ?: component.notation
        val nodeType = request.nodeTypeId?.let {
            nodeTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $it not found")
            }
        }?.also { newNodeType ->
            typeUsageAuthorization.requireCanUseNodeTypeForNotation(newNodeType, notation)
        } ?: component.nodeType

        return NotationBoundEntityWriteSupport.persistUpdate(
            entity = component,
            request = request,
            owner = owner,
            notation = notation,
            mdFileLinkValidator = mdFileLinkValidator,
            applyExtra = { this.nodeType = nodeType },
            save = componentsRepository::save,
            toResponse = notationMapper::toResponse
        )
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete component")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComponent(@PathVariable id: UUID) {
        val component = componentsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Component $id not found")
            }
        accessService.requireCanEditComponent(component)
        componentsRepository.deleteById(id)
    }
}
