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

    @GetMapping
    fun listNotations(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NotationResponse> {
        if (!CurrentUser.isAdmin()) {
            val filtered = notationsRepository.findAll(Pageable.unpaged()).content
                .asSequence()
                .filter { accessService.canViewNotation(it) }
                .filter { ownerId == null || it.owner.id == ownerId }
                .filter { name == null || it.name.contains(name, ignoreCase = true) }
                .toList()
            return filtered.toPage(pageable).map { it.toResponse() }
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
                ).content.any { diagram -> accessService.canViewDiagram(diagram) }

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
        accessService.requireCanEditNotation(source)
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

    private fun checkOwnerOrRole(ownerId: UUID) {
        val currentUserId = CurrentUser.getId() ?: return
        if (currentUserId != ownerId && !CurrentUser.isEditorOrAdmin()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
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
            val hasSharedFromOwner = notationsRepository.findAll(Pageable.unpaged()).content.any {
                it.owner.id == ownerId && accessService.canViewNotation(it)
            }
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
