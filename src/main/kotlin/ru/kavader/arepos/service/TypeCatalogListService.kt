package ru.kavader.arepos.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.controller.normalizedNameOrEmpty
import ru.kavader.arepos.controller.toPage
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@Service
class TypeCatalogListService(
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val notationsRepository: NotationsRepository,
    private val modelsRepository: ModelsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val linkTypesRepository: LinkTypesRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository
) {
    private val viewPermissions = listOf(SharePermission.VIEW, SharePermission.EDIT)

    fun listNodeTypes(
        pageable: Pageable,
        ownerId: UUID?,
        notationId: List<UUID>?,
        modelId: UUID?,
        name: String?
    ): Page<NodeTypes> =
        listCatalog(
            pageable = pageable,
            ownerId = ownerId,
            notationId = notationId,
            modelId = modelId,
            name = name,
            ports = CatalogListPorts(
                id = { it.id },
                ownerId = { it.owner.id },
                name = { it.name },
                findAccessible = { userId, oid, normalizedName, pg ->
                    nodeTypesRepository.findAccessibleForUser(
                        userId = userId,
                        ownerId = oid,
                        name = normalizedName,
                        viewPermissions = viewPermissions,
                        pageable = pg
                    )
                },
                findByOwnerIdIn = nodeTypesRepository::findByOwnerIdIn,
                findByIdIn = nodeTypesRepository::findByIdIn,
                findDistinctTypeIdsByNotationId = componentsRepository::findDistinctNodeTypeIdsByNotationId,
                findDistinctTypeIdsByModelId = { modelIdValue ->
                    nodesRepository.findDistinctNodeTypeIdsByModelId(modelIdValue)
                },
                resolveReadableOwner = { oid ->
                    ownerResolutionService.resolveReadableOwner(oid) { ownerFilterId, userId ->
                        nodeTypesRepository.findAccessibleForUser(
                            userId,
                            ownerFilterId,
                            "",
                            viewPermissions,
                            Pageable.ofSize(1)
                        ).hasContent()
                    }
                },
                adminList = { effectiveOwner, filterName, pg ->
                    when {
                        effectiveOwner != null && filterName != null ->
                            nodeTypesRepository.findByOwnerAndNameContainingIgnoreCase(effectiveOwner, filterName, pg)
                        effectiveOwner != null ->
                            nodeTypesRepository.findByOwner(effectiveOwner, pg)
                        filterName != null ->
                            nodeTypesRepository.findByNameContainingIgnoreCase(filterName, pg)
                        else ->
                            nodeTypesRepository.findAll(pg)
                    }
                }
            )
        )

    fun listLinkTypes(
        pageable: Pageable,
        ownerId: UUID?,
        notationId: List<UUID>?,
        modelId: UUID?,
        name: String?
    ): Page<LinkTypes> =
        listCatalog(
            pageable = pageable,
            ownerId = ownerId,
            notationId = notationId,
            modelId = modelId,
            name = name,
            ports = CatalogListPorts(
                id = { it.id },
                ownerId = { it.owner.id },
                name = { it.name },
                findAccessible = { userId, oid, normalizedName, pg ->
                    linkTypesRepository.findAccessibleForUser(
                        userId = userId,
                        ownerId = oid,
                        name = normalizedName,
                        viewPermissions = viewPermissions,
                        pageable = pg
                    )
                },
                findByOwnerIdIn = linkTypesRepository::findByOwnerIdIn,
                findByIdIn = linkTypesRepository::findByIdIn,
                findDistinctTypeIdsByNotationId = relationsRepository::findDistinctLinkTypeIdsByNotationId,
                findDistinctTypeIdsByModelId = { modelIdValue ->
                    linksRepository.findDistinctLinkTypeIdsByModelId(modelIdValue)
                },
                resolveReadableOwner = { oid ->
                    ownerResolutionService.resolveReadableOwner(oid) { ownerFilterId, userId ->
                        linkTypesRepository.findAccessibleForUser(
                            userId,
                            ownerFilterId,
                            "",
                            viewPermissions,
                            Pageable.ofSize(1)
                        ).hasContent()
                    }
                },
                adminList = { effectiveOwner, filterName, pg ->
                    when {
                        effectiveOwner != null && filterName != null ->
                            linkTypesRepository.findByOwnerAndNameContainingIgnoreCase(effectiveOwner, filterName, pg)
                        effectiveOwner != null ->
                            linkTypesRepository.findByOwner(effectiveOwner, pg)
                        filterName != null ->
                            linkTypesRepository.findByNameContainingIgnoreCase(filterName, pg)
                        else ->
                            linkTypesRepository.findAll(pg)
                    }
                }
            )
        )

    private data class CatalogListPorts<T>(
        val id: (T) -> UUID?,
        val ownerId: (T) -> UUID?,
        val name: (T) -> String,
        val findAccessible: (
            userId: UUID,
            ownerId: UUID?,
            normalizedName: String,
            pageable: Pageable
        ) -> Page<T>,
        val findByOwnerIdIn: (Set<UUID>) -> List<T>,
        val findByIdIn: (Collection<UUID>) -> List<T>,
        val findDistinctTypeIdsByNotationId: (UUID) -> List<UUID>,
        val findDistinctTypeIdsByModelId: (UUID) -> Collection<UUID>,
        val resolveReadableOwner: (UUID?) -> Users?,
        val adminList: (effectiveOwner: Users?, name: String?, pageable: Pageable) -> Page<T>
    )

    private fun <T> listCatalog(
        pageable: Pageable,
        ownerId: UUID?,
        notationId: List<UUID>?,
        modelId: UUID?,
        name: String?,
        ports: CatalogListPorts<T>
    ): Page<T> where T : Any {
        if (!accessService.canViewAdminPanel()) {
            val currentUserId = accessService.currentUserId()
            val normalizedName = name.normalizedNameOrEmpty()
            if (notationId.isNullOrEmpty() && modelId == null) {
                return ports.findAccessible(currentUserId, ownerId, normalizedName, pageable)
            }

            val resolvedModel = modelId?.let { mid ->
                modelsRepository.findById(mid).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Model $mid not found")
                }.also { accessService.requireCanViewModel(it) }
            }
            val notationOwnerIds = mutableSetOf<UUID>()
            val notationTypeIds = mutableSetOf<UUID>()
            notationId?.forEach { requestedNotationId ->
                val notation = notationsRepository.findById(requestedNotationId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $requestedNotationId not found")
                }
                val allowed = when (val model = resolvedModel) {
                    null -> accessService.canViewNotation(notation)
                    else -> accessService.canUseNotationInModelDiagramEditor(notation, model)
                }
                if (!allowed) {
                    return@forEach
                }
                ports.findDistinctTypeIdsByNotationId(requestedNotationId)
                    .forEach { notationTypeIds.add(it) }
                notation.owner.id?.let { notationOwnerIds.add(it) }
            }
            val modelTypeIds = resolvedModel?.let { model ->
                ports.findDistinctTypeIdsByModelId(requireNotNull(model.id)).toSet()
            } ?: emptySet()
            val accessible = ports.findAccessible(
                currentUserId,
                ownerId,
                normalizedName,
                Pageable.unpaged()
            ).content
            val ownerMatched = if (notationOwnerIds.isEmpty()) {
                emptyList()
            } else {
                ports.findByOwnerIdIn(notationOwnerIds)
            }
            val idMatchedIds = notationTypeIds + modelTypeIds
            val idMatched = if (idMatchedIds.isEmpty()) {
                emptyList()
            } else {
                ports.findByIdIn(idMatchedIds)
            }
            return (accessible + ownerMatched + idMatched)
                .asSequence()
                .distinctBy(ports.id)
                .filter { ownerId == null || ports.ownerId(it) == ownerId }
                .filter { normalizedName.isEmpty() || ports.name(it).contains(normalizedName, ignoreCase = true) }
                .toList()
                .toPage(pageable)
        }

        val effectiveOwner = ports.resolveReadableOwner(ownerId)
        return ports.adminList(effectiveOwner, name, pageable)
    }
}
