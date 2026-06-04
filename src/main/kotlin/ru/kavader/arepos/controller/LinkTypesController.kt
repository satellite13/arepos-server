package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.notation.LinkTypeRequest
import ru.kavader.arepos.dto.notation.LinkTypeResponse
import ru.kavader.arepos.dto.notation.LinkTypeUpdateRequest
import ru.kavader.arepos.dto.notation.NotationMapper
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.TypeCatalogListService
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/link-types")
@Tag(name = "Link Types", description = "Link type catalog endpoints")
class LinkTypesController(
    private val linkTypesRepository: LinkTypesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val typeCatalogListService: TypeCatalogListService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val notationMapper: NotationMapper
) {

    @GetMapping
    @Operation(summary = "List link types")
    fun listLinkTypes(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) notationId: List<UUID>?,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<LinkTypeResponse> =
        mapLinkTypesPage(
            typeCatalogListService.listLinkTypes(pageable, ownerId, notationId, modelId, name)
        )

    @GetMapping("/{id}")
    @Operation(summary = "Get link type by id")
    fun getLinkType(@PathVariable id: UUID): LinkTypeResponse =
        linkTypesRepository.findById(id)
            .map {
                if (!accessService.canViewLinkType(it) && !accessService.canUseLinkType(it)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                }
                notationMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create link type")
    fun createLinkType(@RequestBody request: LinkTypeRequest): LinkTypeResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        mdFileLinkValidator.validate(request.attrs)
        val now = Instant.now()
        val saved = linkTypesRepository.save(
            LinkTypes(
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
    @Operation(summary = "Update link type")
    fun updateLinkType(
        @PathVariable id: UUID,
        @RequestBody request: LinkTypeUpdateRequest
    ): LinkTypeResponse {
        val linkType = linkTypesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
            }
        accessService.requireCanEditLinkType(linkType)
        return CatalogTypeWriteSupport.persistUpdate(
            entity = linkType,
            request = request,
            ownerResolutionService = ownerResolutionService,
            mdFileLinkValidator = mdFileLinkValidator,
            save = linkTypesRepository::save,
            toResponse = notationMapper::toResponse
        )
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete link type")
    fun deleteLinkType(@PathVariable id: UUID) {
        val linkType = linkTypesRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $id not found")
        }
        accessService.requireCanEditLinkType(linkType)
        linkTypesRepository.deleteById(id)
    }

    private fun mapLinkTypesPage(page: Page<LinkTypes>): Page<LinkTypeResponse> {
        val permissions = accessService.linkTypeAccessPermissions(page.content)
        val mapped = page.content.map { linkType ->
            val linkTypeId = requireNotNull(linkType.id)
            notationMapper.toResponse(linkType, permissions[linkTypeId])
        }
        return PageImpl(mapped, page.pageable, page.totalElements)
    }
}
