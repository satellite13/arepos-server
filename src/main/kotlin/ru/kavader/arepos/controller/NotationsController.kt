package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.model.*
import ru.kavader.arepos.dto.notation.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.service.MdFileLinkValidator
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import ru.kavader.arepos.util.VersionUtils
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notations")
class NotationsController(
    private val notationsRepository: NotationsRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val diagramsRepository: DiagramsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val relationRulesRepository: RelationRulesRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val notationMapper: NotationMapper
) {
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

    @GetMapping
    fun listNotations(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NotationResponse> {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            return notationsRepository.findAccessibleForUser(
                userId = currentUserId,
                ownerId = ownerId,
                name = name?.trim().orEmpty(),
                viewPermissions = viewPermissions,
                pageable = pageable
            ).map { notationMapper.toResponse(it) }
        }

        val effectiveOwner = resolveReadableOwner(ownerId)
        val notations = when {
            effectiveOwner != null && name != null ->
                notationsRepository.findByOwnerAndNameContainingIgnoreCase(effectiveOwner, name, pageable)
            effectiveOwner != null ->
                notationsRepository.findByOwner(effectiveOwner, pageable)
            name != null ->
                notationsRepository.findByNameContainingIgnoreCase(name, pageable)
            else ->
                notationsRepository.findAll(pageable)
        }
        return notations.map { notationMapper.toResponse(it) }
    }

    @GetMapping("/deleted")
    fun listDeletedNotations(pageable: Pageable): Page<NotationResponse> {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")
        }
        return notationsRepository.findByDeletedTrue(pageable).map { notationMapper.toResponse(it) }
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
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
                EntityGroupResponse(
                    name = sorted.first().name.trim(),
                    versions = sorted.map { notationMapper.toResponse(it) }
                )
            }
            .sortedBy { it.name.lowercase() }

        return GroupedEntityResponse(groups)
    }

    @GetMapping("/{id}")
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
                    if (!accessService.canReferenceNotationForModelDiagram(it, model)) {
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
                    accessService.canReferenceNotationForModelDiagram(notation, model)
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
    fun getNewerVersions(@PathVariable id: UUID): List<NotationResponse> {
        accessService.requireCanViewNotation(
            notationsRepository.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }
        )
        return accessService.filterViewableNotations(notationsRepository.findBySourceId(id))
            .map { notationMapper.toResponse(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
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
        val updated = notationsRepository.save(
            notation.copy(
                name = request.name ?: notation.name,
                version = request.version ?: notation.version,
                attrs = request.attrs ?: notation.attrs,
                owner = owner
            )
        )
        return notationMapper.toResponse(updated)
    }

    @PostMapping("/{sourceId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
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
        val now = Instant.now()
        val newNotation = notationsRepository.save(
            Notations(
                name = request.name,
                version = request.version,
                owner = owner,
                attrs = request.attrs ?: source.attrs,
                createdAt = now,
                updatedAt = now,
                source = source,
                deleted = false
            )
        )

        // Copy components and build oldId → newId map
        val sourceComponents = componentsRepository.findByNotation(source, Pageable.unpaged())
        val componentIdMap = mutableMapOf<UUID, UUID>()
        for (srcComponent in sourceComponents) {
            val saved = componentsRepository.save(
                srcComponent.copy(
                    id = null,
                    notation = newNotation,
                    version = newNotation.version,
                    owner = owner,
                    createdAt = now,
                    updatedAt = now
                )
            )
            componentIdMap[srcComponent.id!!] = saved.id!!
        }

        // Copy relations and build oldId → newId map
        val sourceRelations = relationsRepository.findByNotation(source, Pageable.unpaged())
        val relationIdMap = mutableMapOf<UUID, UUID>()
        for (srcRelation in sourceRelations) {
            val saved = relationsRepository.save(
                srcRelation.copy(
                    id = null,
                    notation = newNotation,
                    version = newNotation.version,
                    owner = owner,
                    createdAt = now,
                    updatedAt = now
                )
            )
            relationIdMap[srcRelation.id!!] = saved.id!!
        }

        // Copy relation rules, remapping relation/component IDs
        for (srcRelation in sourceRelations) {
            val sourceRules = relationRulesRepository.findByRelation(srcRelation, Pageable.unpaged())
            val newRelation = relationsRepository.findById(relationIdMap[srcRelation.id!!]!!)
                .orElseThrow { IllegalStateException("Copied relation not found") }

            for (srcRule in sourceRules) {
                val newFromId = componentIdMap[srcRule.fromComponent.id!!] ?: continue
                val newToId = componentIdMap[srcRule.toComponent.id!!] ?: continue
                val newFrom = componentsRepository.findById(newFromId)
                    .orElseThrow { IllegalStateException("Copied component not found") }
                val newTo = componentsRepository.findById(newToId)
                    .orElseThrow { IllegalStateException("Copied component not found") }

                relationRulesRepository.save(
                    srcRule.copy(
                        id = null,
                        relation = newRelation,
                        fromComponent = newFrom,
                        toComponent = newTo,
                        owner = owner,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }

        return notationMapper.toResponse(newNotation)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
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

}
