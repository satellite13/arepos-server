package ru.kavader.arepos.controller

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
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationRulesRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.MdFileLinkValidator
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notations")
class NotationsController(
    private val notationsRepository: NotationsRepository,
    private val usersRepository: UsersRepository,
    private val diagramsRepository: DiagramsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val relationRulesRepository: RelationRulesRepository,
    private val accessService: ResourceAccessService,
    private val mdFileLinkValidator: MdFileLinkValidator
) {
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

    @GetMapping
    fun listNotations(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NotationResponse> {
        if (!CurrentUser.isAdmin()) {
            val currentUserId = accessService.currentUserId()
            return notationsRepository.findAccessibleForUser(
                userId = currentUserId,
                ownerId = ownerId,
                name = name?.trim().orEmpty(),
                viewPermissions = viewPermissions,
                pageable = pageable
            ).map { it.toResponse() }
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
        return notations.map { it.toResponse() }
    }

    @GetMapping("/deleted")
    fun listDeletedNotations(pageable: Pageable): Page<NotationResponse> {
        if (!CurrentUser.isAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")
        }
        return notationsRepository.findByDeletedTrue(pageable).map { it.toResponse() }
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun permanentDeleteNotation(@PathVariable id: UUID) {
        if (!CurrentUser.isAdmin()) {
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
        val allNotations = if (!CurrentUser.isAdmin()) {
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
                    versions = sorted.map { it.toResponse() }
                )
            }
            .sortedBy { it.name.lowercase() }

        return GroupedEntityResponse(groups)
    }

    @GetMapping("/{id}")
    fun getNotation(@PathVariable id: UUID): NotationResponse =
        notationsRepository.findById(id)
            .map {
                accessService.requireCanViewNotation(it)
                it.toResponse()
            }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }

    @GetMapping("/{id}/meta")
    fun getNotationMeta(@PathVariable id: UUID): NotationMetaResponse =
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

                if (!canViewDirectly && !hasVisibleDiagramWithNotation) {
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
            .map { it.toResponse() }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNotation(@RequestBody request: NotationRequest): NotationResponse {
        val currentUserId = accessService.currentUserId()
        val resolvedOwnerId = if (CurrentUser.isAdmin()) {
            request.ownerId ?: currentUserId
        } else {
            currentUserId
        }
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }
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
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateNotation(
        @PathVariable id: UUID,
        @RequestBody request: NotationUpdateRequest
    ): NotationResponse {
        val notation = notationsRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $id not found")
            }
        accessService.requireCanEditNotation(notation)

        val owner = if (CurrentUser.isAdmin()) {
            request.ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: notation.owner
        } else {
            notation.owner
        }

        mdFileLinkValidator.validate(request.attrs)
        val updated = notationsRepository.save(
            notation.copy(
                name = request.name ?: notation.name,
                version = request.version ?: notation.version,
                attrs = request.attrs ?: notation.attrs,
                owner = owner
            )
        )
        return updated.toResponse()
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
        val currentUserId = accessService.currentUserId()
        val resolvedOwnerId = if (CurrentUser.isAdmin()) {
            request.ownerId ?: currentUserId
        } else {
            currentUserId
        }
        val owner = usersRepository.findById(resolvedOwnerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
            }

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

        return newNotation.toResponse()
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

    private val compareNotationsByVersionDesc: Comparator<Notations> =
        compareBy<Notations> { parseSemver(it.version) == null }
            .thenByDescending { parseSemver(it.version)?.first ?: 0 }
            .thenByDescending { parseSemver(it.version)?.second ?: 0 }
            .thenByDescending { parseSemver(it.version)?.third ?: 0 }
            .thenByDescending { it.version }

    private fun parseSemver(version: String): Triple<Int, Int, Int>? {
        val parts = version.trim().split(".")
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        return Triple(major, minor, patch)
    }

    private fun resolveReadableOwner(ownerId: UUID?): ru.kavader.arepos.model.Users? {
        val currentUserId = CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

        if (CurrentUser.isAdmin()) {
            return ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            }
        }

        if (ownerId != null && ownerId != currentUserId) {
            val hasSharedFromOwner = notationsRepository.existsAccessibleByOwnerForUser(
                ownerId = ownerId,
                userId = currentUserId,
                viewPermissions = viewPermissions
            )
            if (!hasSharedFromOwner) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            }
            return null
        }

        return usersRepository.findById(currentUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $currentUserId not found")
        }
    }

    private fun Notations.toResponse() = NotationResponse(
        id = requireNotNull(id),
        name = name,
        version = version,
        ownerId = owner.id!!,
        accessPermission = accessService.notationAccessPermission(this),
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sourceId = source?.id
    )
}

data class NotationRequest(
    val name: String,
    val version: String,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NotationUpdateRequest(
    val name: String? = null,
    val version: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NotationResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val accessPermission: String? = null,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val sourceId: UUID? = null
)

data class NotationMetaResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val ownerId: UUID,
    val ownerEmail: String
)
