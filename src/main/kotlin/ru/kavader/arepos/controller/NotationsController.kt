package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.EntityGroupResponse
import ru.kavader.arepos.dto.model.GroupedEntityResponse
import ru.kavader.arepos.dto.notation.*
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.NotationCopyService
import ru.kavader.arepos.util.VersionUtils
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/notations")
@Tag(name = "Notations", description = "Notation and notation-version endpoints")
class NotationsController(
    private val notationsRepository: NotationsRepository,
    private val modelsRepository: ModelsRepository,
    private val diagramsRepository: DiagramsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val notationCopyService: NotationCopyService,
    private val notationMapper: NotationMapper
) {
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

    @GetMapping
    @Operation(summary = "List notations")
    fun listNotations(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NotationResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            val page = notationsRepository.findAccessibleForUser(
                userId = currentUserId,
                ownerId = ownerId,
                name = name?.trim().orEmpty(),
                viewPermissions = viewPermissions,
                pageable = pageable
            )
            return mapNotationsPage(page)
        }

        val notations = listPageByOwnerAndName(
            effectiveOwner = resolveReadableOwner(ownerId),
            name = name,
            pageable = pageable,
            queries = OwnerNamePageQueries(
                byOwnerAndName = notationsRepository::findByOwnerAndNameContainingIgnoreCase,
                byOwner = notationsRepository::findByOwner,
                byName = notationsRepository::findByNameContainingIgnoreCase,
                all = notationsRepository::findAll
            )
        )
        return mapNotationsPage(notations)
    }

    @GetMapping("/deleted")
    @Operation(summary = "List soft-deleted notations (admin)")
    fun listDeletedNotations(pageable: Pageable): Page<NotationResponse> {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")
        }
        return mapNotationsPage(notationsRepository.findByDeletedTrue(pageable))
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @Operation(summary = "Permanently delete notation (admin)")
    fun permanentDeleteNotation(@PathVariable id: UUID) {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")
        }
        val notation = notationsRepository.findByIdIncludingDeleted(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }
        notationsRepository.delete(notation)
    }

    @GetMapping("/grouped")
    @Operation(summary = "List notations grouped by name")
    fun listNotationsGrouped(): GroupedEntityResponse<NotationResponse> {
        val allNotations = if (!accessService.canViewAdminPanel()) {
            notationsRepository.findAccessibleForUser(
                userId = accessService.currentUserId(),
                ownerId = null,
                name = "",
                viewPermissions = viewPermissions,
                pageable = Pageable.unpaged()
            ).content
        } else {
            notationsRepository.findAll(Pageable.unpaged()).content
        }

        val groups = allNotations
            .groupBy { it.name.trim().lowercase() }
            .map { (_, notations) ->
                val sorted = notations.sortedWith(compareNotationsByVersionDesc)
                val permissions = accessService.notationAccessPermissions(sorted)
                EntityGroupResponse(
                    name = sorted.first().name.trim(),
                    versions = sorted.map { notation ->
                        val notationId = requireNotNull(notation.id)
                        notationMapper.toResponse(notation, permissions[notationId])
                    }
                )
            }
            .sortedBy { it.name.lowercase() }

        return GroupedEntityResponse(groups)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notation by id")
    fun getNotation(
        @PathVariable id: UUID,
        @RequestParam(required = false) modelId: UUID?
    ): NotationResponse =
        notationsRepository.findById(id)
            .map {
                if (modelId != null) {
                    val model = modelsRepository.findById(modelId).orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
                    }
                    accessService.requireCanViewModel(model)
                    if (!accessService.canUseNotationInModelDiagramEditor(it, model)) {
                        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                    }
                } else {
                    accessService.requireCanViewNotation(it)
                }
                notationMapper.toResponse(it)
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }

    @GetMapping("/{id}/meta")
    @Operation(summary = "Get notation lightweight metadata")
    fun getNotationMeta(
        @PathVariable id: UUID,
        @RequestParam(required = false) modelId: UUID?
    ): NotationMetaResponse =
        notationsRepository.findById(id)
            .map { notation ->
                val canViewDirectly = accessService.canViewNotation(notation)
                val hasVisibleDiagramWithNotation = diagramsRepository.findByFilters(
                    ownerId = null,
                    modelId = null,
                    nodeId = null,
                    notationId = id,
                    name = "",
                    pageable = Pageable.unpaged()
                ).content.let { diagrams -> accessService.filterViewableDiagrams(diagrams).isNotEmpty() }
                val viaModelEditor = modelId?.let { mid ->
                    val model = modelsRepository.findById(mid).orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Model $mid not found")
                    }
                    accessService.requireCanViewModel(model)
                    accessService.canUseNotationInModelDiagramEditor(notation, model)
                } ?: false

                if (!canViewDirectly && !hasVisibleDiagramWithNotation && !viaModelEditor) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
                }

                NotationMetaResponse(
                    id = requireNotNull(notation.id),
                    name = notation.name,
                    version = notation.version,
                    ownerId = notation.owner.id!!,
                    ownerEmail = notation.owner.email
                )
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }

    @GetMapping("/{id}/newer-versions")
    @Operation(summary = "Get newer notation versions derived from source")
    fun getNewerVersions(@PathVariable id: UUID): List<NotationResponse> {
        accessService.requireCanViewNotation(
            notationsRepository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }
        )
        return accessService.filterViewableNotations(notationsRepository.findBySourceId(id))
            .let { notations ->
                val permissions = accessService.notationAccessPermissions(notations)
                notations.map { notation ->
                    val notationId = requireNotNull(notation.id)
                    notationMapper.toResponse(notation, permissions[notationId])
                }
            }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create notation")
    fun createNotation(@RequestBody @Valid request: NotationRequest): NotationResponse {
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)
        mdFileLinkValidator.validate(request.attrs)
        // Конфликт только с неудалёнными: версия, занятая удалённой нотацией, допустима
        if (notationsRepository.existsByNameAndVersion(request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Notation with name '${request.name}' and version '${request.version}' already exists"
            )
        }
        val now = Instant.now()
        val saved = notationsRepository.save(
            Notations(
                name = request.name,
                version = request.version,
                owner = owner,
                attrs = request.attrs,
                createdAt = now,
                updatedAt = now,
                deleted = false
            )
        )
        return notationMapper.toResponse(saved)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update notation")
    fun updateNotation(
        @PathVariable id: UUID,
        @RequestBody @Valid request: NotationUpdateRequest
    ): NotationResponse {
        val notation = notationsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }
        accessService.requireCanEditNotation(notation)

        val owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, notation.owner)

        mdFileLinkValidator.validate(request.attrs)
        notation.name = request.name ?: notation.name
        notation.version = request.version ?: notation.version
        notation.attrs = request.attrs ?: notation.attrs
        notation.owner = owner
        val updated = notationsRepository.save(notation)
        return notationMapper.toResponse(updated)
    }

    @PostMapping("/{sourceId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Create notation by copying source notation")
    fun copyNotation(
        @PathVariable sourceId: UUID,
        @RequestBody request: NotationRequest
    ): NotationResponse {
        val source = notationsRepository.findById(sourceId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Source notation $sourceId not found")
            }
        // Копирование создает новую нотацию и не изменяет исходник, достаточно прав на чтение источника.
        accessService.requireCanViewNotation(source)
        // Конфликт только с неудалёнными: версия, занятая удалённой нотацией, допустима
        if (notationsRepository.existsByNameAndVersion(request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Notation with name '${request.name}' and version '${request.version}' already exists"
            )
        }
        val owner = ownerResolutionService.resolveOwnerForCreate(request.ownerId)

        mdFileLinkValidator.validate(request.attrs ?: source.attrs)
        val copied = notationCopyService.copyNotation(
            source = source,
            owner = owner,
            name = request.name,
            version = request.version,
            attrs = request.attrs
        )
        return notationMapper.toResponse(copied)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @Operation(summary = "Soft-delete notation")
    fun deleteNotation(@PathVariable id: UUID) {
        val notation = notationsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }
        accessService.requireCanEditNotation(notation)
        val deletedCount = notationsRepository.softDeleteById(id)
        if (deletedCount == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
        }
    }

    private val compareNotationsByVersionDesc = VersionUtils.semverDescComparator<Notations> { it.version }

    private fun resolveReadableOwner(ownerId: UUID?): ru.kavader.arepos.model.Users? =
        ownerResolutionService.resolveReadableOwner(ownerId) { oid, uid ->
            notationsRepository.existsAccessibleByOwnerForUser(oid, uid, viewPermissions)
        }

    private fun mapNotationsPage(page: Page<Notations>): Page<NotationResponse> {
        val permissions = accessService.notationAccessPermissions(page.content)
        val mapped = page.content.map { notation ->
            val notationId = requireNotNull(notation.id)
            notationMapper.toResponse(notation, permissions[notationId])
        }
        return PageImpl(mapped, page.pageable, page.totalElements)
    }

}
