package ru.kavader.arepos.service

import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.search.CatalogSearchHit
import ru.kavader.arepos.dto.search.CatalogSearchResponse
import ru.kavader.arepos.dto.search.ModelSearchHit
import ru.kavader.arepos.dto.search.ModelSearchResponse
import ru.kavader.arepos.dto.search.NotationSearchHit
import ru.kavader.arepos.dto.search.NotationSearchResponse
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@Service
class SearchService(
    private val modelsRepository: ModelsRepository,
    private val notationsRepository: NotationsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val accessService: ResourceAccessService
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
        val CATALOG_KINDS = setOf("models", "notations")
        val MODEL_KINDS = setOf("nodes", "links", "diagrams")
        val NOTATION_KINDS = setOf("components", "relations")
    }

    @Transactional(readOnly = true)
    fun searchCatalog(qRaw: String?, kindsRaw: String?, limitRaw: Int?): CatalogSearchResponse {
        val q = normalizeQuery(qRaw)
        val limit = normalizeLimit(limitRaw)
        val kinds = parseKinds(kindsRaw, CATALOG_KINDS)
        // Fetch up to MAX_LIMIT per kind, then ACL-filter, then apply caller limit.
        val pageable = PageRequest.of(0, MAX_LIMIT)

        val hits = mutableListOf<CatalogSearchHit>()

        if ("models" in kinds) {
            val page = modelsRepository.findByNameContainingIgnoreCase(q, pageable)
            val access = accessService.canViewModels(page.content)
            val readableIds = accessService.mcpReadableModelIds()
            hits += page.content
                .filter { model -> access[model.id] == true }
                .filter { model -> readableIds == null || model.id in readableIds }
                .map { model ->
                    CatalogSearchHit(
                        kind = "model",
                        id = model.id!!,
                        name = model.name,
                        version = model.version
                    )
                }
        }

        if ("notations" in kinds) {
            val page = notationsRepository.findByNameContainingIgnoreCase(q, pageable)
            val access = accessService.canViewNotations(page.content)
            hits += page.content
                .filter { notation -> access[notation.id] == true }
                .map { notation ->
                    CatalogSearchHit(
                        kind = "notation",
                        id = notation.id!!,
                        name = notation.name,
                        version = notation.version
                    )
                }
        }

        val ordered = hits.sortedWith(
            compareBy<CatalogSearchHit> { it.kind }
                .thenBy { it.name.lowercase() }
                .thenByDescending { it.version }
        )
        return CatalogSearchResponse(
            q = q,
            limit = limit,
            totalEstimate = ordered.size,
            hits = ordered.take(limit)
        )
    }

    @Transactional(readOnly = true)
    fun searchModel(modelId: UUID, qRaw: String?, kindsRaw: String?, limitRaw: Int?): ModelSearchResponse {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)

        val q = normalizeQuery(qRaw)
        val limit = normalizeLimit(limitRaw)
        val kinds = parseKinds(kindsRaw, MODEL_KINDS)
        val pageable = PageRequest.of(0, limit)

        val hits = mutableListOf<ModelSearchHit>()
        var totalEstimate = 0

        if ("nodes" in kinds) {
            val page = nodesRepository.searchByModelIdAndName(modelId, q, pageable)
            totalEstimate += page.totalElements.toInt()
            hits += page.content.map { node ->
                ModelSearchHit(
                    kind = "node",
                    id = node.id!!,
                    name = node.name,
                    typeName = node.nodeType.name,
                    parentId = node.parentNode?.id
                )
            }
        }

        if ("links" in kinds) {
            val page = linksRepository.searchByModelIdAndEndpointNames(modelId, q, pageable)
            totalEstimate += page.totalElements.toInt()
            hits += page.content.map { link ->
                ModelSearchHit(
                    kind = "link",
                    id = link.id!!,
                    name = null,
                    typeName = link.linkType.name,
                    sourceId = link.source.id,
                    targetId = link.target.id,
                    sourceName = link.source.name,
                    targetName = link.target.name
                )
            }
        }

        if ("diagrams" in kinds) {
            val page = diagramsRepository.findByFilters(
                null,
                modelId,
                null,
                null,
                q,
                pageable
            )
            totalEstimate += page.totalElements.toInt()
            hits += page.content.map { diagram ->
                ModelSearchHit(
                    kind = "diagram",
                    id = diagram.id!!,
                    name = diagram.name,
                    notationName = diagram.notation.name
                )
            }
        }

        return ModelSearchResponse(
            modelId = modelId,
            q = q,
            limit = limit,
            totalEstimate = totalEstimate,
            hits = hits.take(limit)
        )
    }

    @Transactional(readOnly = true)
    fun searchNotation(notationId: UUID, qRaw: String?, kindsRaw: String?, limitRaw: Int?): NotationSearchResponse {
        val notation = notationsRepository.findById(notationId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $notationId not found")
        }
        accessService.requireCanViewNotation(notation)

        val q = normalizeQuery(qRaw)
        val limit = normalizeLimit(limitRaw)
        val kinds = parseKinds(kindsRaw, NOTATION_KINDS)
        val pageable = PageRequest.of(0, limit)

        val hits = mutableListOf<NotationSearchHit>()
        var totalEstimate = 0

        if ("components" in kinds) {
            val page = componentsRepository.searchByNotationIdAndName(notationId, q, pageable)
            totalEstimate += page.totalElements.toInt()
            hits += page.content.map { component ->
                NotationSearchHit(
                    kind = "component",
                    id = component.id!!,
                    name = component.name,
                    version = component.version,
                    nodeTypeId = component.nodeType.id
                )
            }
        }

        if ("relations" in kinds) {
            val page = relationsRepository.searchByNotationIdAndName(notationId, q, pageable)
            totalEstimate += page.totalElements.toInt()
            hits += page.content.map { relation ->
                NotationSearchHit(
                    kind = "relation",
                    id = relation.id!!,
                    name = relation.name,
                    version = relation.version,
                    linkTypeId = relation.linkType.id
                )
            }
        }

        return NotationSearchResponse(
            notationId = notationId,
            q = q,
            limit = limit,
            totalEstimate = totalEstimate,
            hits = hits.take(limit)
        )
    }

    private fun normalizeQuery(qRaw: String?): String {
        val q = qRaw?.trim().orEmpty()
        if (q.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'q' must be non-empty")
        }
        return q
    }

    private fun normalizeLimit(limitRaw: Int?): Int {
        val limit = limitRaw ?: DEFAULT_LIMIT
        if (limit < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be >= 1")
        }
        return minOf(limit, MAX_LIMIT)
    }

    private fun parseKinds(kindsRaw: String?, allowed: Set<String>): Set<String> {
        if (kindsRaw.isNullOrBlank()) {
            return allowed
        }
        val parsed = kindsRaw.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val unknown = parsed - allowed
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unknown kinds: ${unknown.joinToString()}. Allowed: ${allowed.joinToString()}"
            )
        }
        if (parsed.isEmpty()) {
            return allowed
        }
        return parsed
    }
}
