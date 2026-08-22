package ru.kavader.arepos.service.diagramcopy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramCopyCommitRequest
import ru.kavader.arepos.dto.model.DiagramCopyCommitResponse
import ru.kavader.arepos.dto.model.DiagramCopyEntityKind
import ru.kavader.arepos.dto.model.DiagramCopyNotationRemapReport
import ru.kavader.arepos.dto.model.DiagramCopyPreviewRequest
import ru.kavader.arepos.dto.model.DiagramCopyPreviewResponse
import ru.kavader.arepos.dto.model.DiagramCopyResolutionAction
import ru.kavader.arepos.dto.model.DiagramCopyWarning
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.MdFileLinkValidator
import ru.kavader.arepos.service.modelbatch.DiagramAttrsRemapper
import java.time.Instant
import java.util.UUID

@Service
class DiagramCopyService(
    private val diagramsRepository: DiagramsRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val notationsRepository: NotationsRepository,
    private val componentsRepository: ComponentsRepository,
    private val relationsRepository: RelationsRepository,
    private val accessService: ResourceAccessService,
    private val ownerResolutionService: OwnerResolutionService,
    private val typeUsageAuthorization: TypeUsageAuthorization,
    private val matcher: DiagramCopyMatcher,
    private val folderAllocator: DiagramCopyFolderAllocator,
    private val notationRemapper: DiagramCopyNotationRemapper,
    private val diagramAttrsRemapper: DiagramAttrsRemapper,
    private val modelMapper: ModelMapper,
    private val objectMapper: ObjectMapper,
    private val mdFileLinkValidator: MdFileLinkValidator
) {

    fun preview(targetModelId: UUID, request: DiagramCopyPreviewRequest): DiagramCopyPreviewResponse {
        val context = loadContext(
            targetModelId = targetModelId,
            sourceDiagramId = request.sourceDiagramId,
            targetNotationId = request.targetNotationId
        )
        val result = matcher.buildPreview(
            sourceNodes = context.sourceNodes.map(::toMatchableNode),
            sourceLinks = context.sourceLinks.map(::toMatchableLink),
            targetNodes = context.targetNodes.map(::toMatchableNode),
            targetLinks = context.targetLinks.map(::toMatchableLink),
            edges = context.edgeRefs,
            resolutions = request.resolutions
        )
        val notationMapping = notationMapping(context)
        val remappedAttrs = notationRemapper.remapDiagramAttrs(
            attrs = context.sourceDiagram.attrs,
            sourceNotationId = requireNotNull(context.sourceDiagram.notation.id),
            targetNotationId = requireNotNull(context.targetNotation.id),
            componentIdMap = notationMapping.componentIds,
            relationIdMap = notationMapping.relationIds
        )

        return DiagramCopyPreviewResponse(
            sourceDiagramId = requireNotNull(context.sourceDiagram.id),
            sourceDiagramName = context.sourceDiagram.name,
            sourceDiagramVersion = context.sourceDiagram.version,
            suggestedName = context.sourceDiagram.name,
            suggestedVersion = suggestVersion(context.targetModel, context.sourceDiagram),
            nodes = result.nodes,
            links = result.links,
            blockers = result.blockers,
            notationRemap = notationMapping.report,
            warnings = (remappedAttrs.warnings + notationMapping.warnings).distinct(),
            canCommit = result.canCommit
        )
    }

    @Transactional
    fun commit(targetModelId: UUID, request: DiagramCopyCommitRequest): DiagramCopyCommitResponse {
        val context = loadContext(
            targetModelId = targetModelId,
            sourceDiagramId = request.sourceDiagramId,
            targetNotationId = request.targetNotationId
        )
        val result = matcher.buildPreview(
            sourceNodes = context.sourceNodes.map(::toMatchableNode),
            sourceLinks = context.sourceLinks.map(::toMatchableLink),
            targetNodes = context.targetNodes.map(::toMatchableNode),
            targetLinks = context.targetLinks.map(::toMatchableLink),
            edges = context.edgeRefs,
            resolutions = request.resolutions
        )
        if (!result.canCommit || result.blockers.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Diagram copy has unresolved entities")
        }
        if (diagramsRepository.existsByModelAndNameAndVersion(context.targetModel, request.name, request.version)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                diagramNameVersionConflict(request.name, request.version)
            )
        }

        val sourceNodesById = context.sourceNodes.associateBy { requireNotNull(it.id) }
        val sourceLinksById = context.sourceLinks.associateBy { requireNotNull(it.id) }
        val targetNodesById = context.targetNodes.associateBy { requireNotNull(it.id) }
        val targetLinksById = context.targetLinks.associateBy { requireNotNull(it.id) }
        val nodeActions = result.nodes.associateBy { it.sourceId }
        val linkActions = result.links.associateBy { it.sourceId }

        val matchedNodes = resolveNodeMatches(
            previews = result.nodes,
            sourceNodesById = sourceNodesById,
            targetNodesById = targetNodesById
        )
        val matchedLinks = resolveLinkMatches(
            previews = result.links,
            sourceLinksById = sourceLinksById,
            targetLinksById = targetLinksById
        )
        val nodesToCreate = result.nodes
            .filter { it.effectiveAction == DiagramCopyResolutionAction.CREATE }
            .sortedBy { it.sourceId.toString() }
        val linksToCreate = result.links
            .filter { it.effectiveAction == DiagramCopyResolutionAction.CREATE }
            .sortedBy { it.sourceId.toString() }
        nodesToCreate
            .map { sourceNodesById.getValue(it.sourceId).nodeType }
            .distinctBy { it.id }
            .forEach { typeUsageAuthorization.requireCanUseNodeTypeForModel(it, context.targetModel) }
        linksToCreate
            .map { sourceLinksById.getValue(it.sourceId).linkType }
            .distinctBy { it.id }
            .forEach { typeUsageAuthorization.requireCanUseLinkTypeForModel(it, context.targetModel) }
        val notationMapping = notationMapping(context)
        val owner = ownerResolutionService.resolveOwnerForCreate(null)
        val now = Instant.now()
        val parent = resolveCreateParent(context.targetModel, context.targetNodes, request.createParentNodeId)
        val diagramNode = request.nodeId?.let { nodeId ->
            targetNodesById[nodeId] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Diagram node $nodeId is not in target model")
        }
        val allSourceNodesById = nodesRepository.findByModelIdOrdered(
            requireNotNull(context.sourceDiagram.model.id),
            Pageable.unpaged()
        ).content.associateBy { requireNotNull(it.id) }
        val mutableTargetNodesById = targetNodesById.toMutableMap()

        val nodeIdMap = matchedNodes.toMutableMap()
        val createdNodes = mutableListOf<Nodes>()
        val occupiedNodeStableIds = context.targetNodes.mapTo(mutableSetOf()) { it.stableId }
        val folderSession = folderAllocator.openSession(
            targetModel = context.targetModel,
            createBaseParent = parent,
            allSourceNodesById = allSourceNodesById,
            targetNodesById = mutableTargetNodesById,
            owner = owner,
            now = now,
            occupiedStableIds = occupiedNodeStableIds
        )
        nodesToCreate.forEach { preview ->
                val source = sourceNodesById[preview.sourceId]
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Source node ${preview.sourceId} is unavailable")
                val stableId = source.stableId.takeUnless { it in occupiedNodeStableIds } ?: UUID.randomUUID()
                occupiedNodeStableIds += stableId
                val remappedAttrs = notationRemapper.remapNodeAttrs(
                    attrs = source.attrs,
                    sourceNotationId = requireNotNull(context.sourceDiagram.notation.id),
                    targetNotationId = requireNotNull(context.targetNotation.id),
                    componentIdMap = notationMapping.componentIds
                )
                val saved = nodesRepository.save(
                    Nodes(
                        stableId = stableId,
                        name = source.name,
                        createdAt = now,
                        updatedAt = now,
                        attrs = remappedAttrs.attrs,
                        parentNode = folderSession.parentForCreatedNode(source),
                        model = context.targetModel,
                        owner = owner,
                        nodeType = source.nodeType
                    )
                )
                nodeIdMap[preview.sourceId] = requireNotNull(saved.id)
                createdNodes += saved
                mutableTargetNodesById[requireNotNull(saved.id)] = saved
            }
        createdNodes += folderSession.createdFolders

        val nodesByTargetId = mutableTargetNodesById
        validateMatchedLinkEndpoints(
            matchedLinks = matchedLinks,
            sourceLinksById = sourceLinksById,
            targetLinksById = targetLinksById,
            nodeIdMap = nodeIdMap
        )
        val linkIdMap = matchedLinks.toMutableMap()
        val createdLinks = mutableListOf<Links>()
        val occupiedLinkStableIds = context.targetLinks.mapTo(mutableSetOf()) { it.stableId }
        linksToCreate.forEach { preview ->
                val source = sourceLinksById[preview.sourceId]
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Source link ${preview.sourceId} is unavailable")
                val sourceId = requireNotNull(source.source.id)
                val targetId = requireNotNull(source.target.id)
                val copiedSource = nodeIdMap[sourceId]?.let(nodesByTargetId::get)
                val copiedTarget = nodeIdMap[targetId]?.let(nodesByTargetId::get)
                if (copiedSource == null || copiedTarget == null) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot create link ${preview.sourceId}: one or both endpoints are unresolved"
                    )
                }
                val stableId = source.stableId.takeUnless { it in occupiedLinkStableIds } ?: UUID.randomUUID()
                occupiedLinkStableIds += stableId
                val remappedAttrs = notationRemapper.remapLinkAttrs(
                    attrs = source.attrs,
                    sourceNotationId = requireNotNull(context.sourceDiagram.notation.id),
                    targetNotationId = requireNotNull(context.targetNotation.id),
                    relationIdMap = notationMapping.relationIds
                )
                val saved = linksRepository.save(
                    Links(
                        stableId = stableId,
                        source = copiedSource,
                        target = copiedTarget,
                        attrs = remappedAttrs.attrs,
                        createdAt = now,
                        updatedAt = now,
                        owner = owner,
                        linkType = source.linkType,
                        model = context.targetModel
                    )
                )
                linkIdMap[preview.sourceId] = requireNotNull(saved.id)
                createdLinks += saved
            }

        val retainedNodeIds = nodeActions
            .filterValues { it.effectiveAction != DiagramCopyResolutionAction.SKIP }
            .keys
        val retainedLinkIds = linkActions
            .filterValues { it.effectiveAction != DiagramCopyResolutionAction.SKIP }
            .keys
        val filteredAttrs = filterSkippedInstances(
            context.sourceDiagram.attrs,
            retainedNodeIds = retainedNodeIds,
            retainedLinkIds = retainedLinkIds
        )
        val modelRemappedAttrs = diagramAttrsRemapper.remap(
            attrs = filteredAttrs,
            nodeIdMap = nodeIdMap.mapKeys { it.key.toString() },
            linkIdMap = linkIdMap.mapKeys { it.key.toString() }
        )
        val notationRemappedAttrs = notationRemapper.remapDiagramAttrs(
            attrs = modelRemappedAttrs,
            sourceNotationId = requireNotNull(context.sourceDiagram.notation.id),
            targetNotationId = requireNotNull(context.targetNotation.id),
            componentIdMap = notationMapping.componentIds,
            relationIdMap = notationMapping.relationIds
        ).attrs
        mdFileLinkValidator.validate(notationRemappedAttrs)
        val diagram = diagramsRepository.save(
            Diagrams(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = notationRemappedAttrs,
                version = request.version,
                owner = owner,
                deleted = false,
                model = context.targetModel,
                notation = context.targetNotation,
                node = diagramNode
            )
        )
        return DiagramCopyCommitResponse(
            diagram = modelMapper.toResponse(diagram),
            createdNodeIds = createdNodes.map { requireNotNull(it.id) },
            createdLinkIds = createdLinks.map { requireNotNull(it.id) }
        )
    }

    private fun loadContext(
        targetModelId: UUID,
        sourceDiagramId: UUID,
        targetNotationId: UUID
    ): CopyContext {
        val sourceDiagram = diagramsRepository.findById(sourceDiagramId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $sourceDiagramId not found")
        }
        accessService.requireCanViewDiagram(sourceDiagram)
        val targetModel = modelsRepository.findById(targetModelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $targetModelId not found")
        }
        accessService.requireCanEditModel(targetModel)
        val targetNotation = notationsRepository.findById(targetNotationId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Notation $targetNotationId not found")
        }
        accessService.requireCanReferenceNotationForModelDiagram(targetNotation, targetModel)

        val references = collectReferences(sourceDiagram.attrs)
        val allSourceNodes = nodesRepository.findByModelIdOrdered(
            requireNotNull(sourceDiagram.model.id),
            Pageable.unpaged()
        ).content
        val allSourceLinks = linksRepository.findByModelOrderByIdAsc(sourceDiagram.model, Pageable.unpaged()).content
        val sourceLinksById = allSourceLinks.associateBy { requireNotNull(it.id) }
        val referencedNodeIds = references.nodeIds.toMutableSet()
        references.edges.forEach { edge ->
            sourceLinksById[edge.modelLinkId]?.let { link ->
                referencedNodeIds += requireNotNull(link.source.id)
                referencedNodeIds += requireNotNull(link.target.id)
            }
        }
        val sourceNodes = allSourceNodes.filter { requireNotNull(it.id) in referencedNodeIds }
        val sourceLinks = allSourceLinks.filter { requireNotNull(it.id) in references.linkIds }
        val targetNodes = nodesRepository.findByModelIdOrdered(targetModelId, Pageable.unpaged()).content
        val targetLinks = linksRepository.findByModelOrderByIdAsc(targetModel, Pageable.unpaged()).content

        val sourceComponents = componentsRepository.findByNotation(sourceDiagram.notation, Pageable.unpaged()).content
        val targetComponents = componentsRepository.findByNotation(targetNotation, Pageable.unpaged()).content
        val sourceRelations = relationsRepository.findByNotation(sourceDiagram.notation, Pageable.unpaged()).content
        val targetRelations = relationsRepository.findByNotation(targetNotation, Pageable.unpaged()).content
        return CopyContext(
            sourceDiagram = sourceDiagram,
            targetModel = targetModel,
            targetNotation = targetNotation,
            sourceNodes = sourceNodes,
            sourceLinks = sourceLinks,
            targetNodes = targetNodes,
            targetLinks = targetLinks,
            edgeRefs = references.edges,
            sourceComponents = sourceComponents,
            targetComponents = targetComponents,
            sourceRelations = sourceRelations,
            targetRelations = targetRelations
        )
    }

    private fun notationMapping(context: CopyContext): NotationMapping {
        val (componentIds, unmappedComponents) = notationRemapper.buildComponentIdMap(
            context.sourceComponents,
            context.targetComponents
        )
        val (relationIds, unmappedRelations) = notationRemapper.buildRelationIdMap(
            context.sourceRelations,
            context.targetRelations
        )
        return NotationMapping(
            componentIds = componentIds,
            relationIds = relationIds,
            report = DiagramCopyNotationRemapReport(
                mappedComponents = componentIds.size,
                unmappedComponents = unmappedComponents,
                mappedRelations = relationIds.size,
                unmappedRelations = unmappedRelations
            ),
            warnings = unmappedComponents.map {
                DiagramCopyWarning("NOTATION_COMPONENT_NOT_MAPPED", "Notation component '$it' was not mapped")
            } + unmappedRelations.map {
                DiagramCopyWarning("NOTATION_RELATION_NOT_MAPPED", "Notation relation '$it' was not mapped")
            }
        )
    }

    private fun resolveCreateParent(targetModel: Models, targetNodes: List<Nodes>, requestedParentId: UUID?): Nodes {
        if (requestedParentId != null) {
            return targetNodes.firstOrNull { it.id == requestedParentId }
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Create parent node $requestedParentId is not in target model"
                )
        }
        return targetNodes.filter { it.parentNode == null }.singleOrNull()
            ?: throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Target model ${targetModel.id} must have exactly one root node"
            )
    }

    private fun resolveNodeMatches(
        previews: List<ru.kavader.arepos.dto.model.DiagramCopyEntityPreview>,
        sourceNodesById: Map<UUID, Nodes>,
        targetNodesById: Map<UUID, Nodes>
    ): Map<UUID, UUID> {
        val matches = previews.filter { it.effectiveAction == DiagramCopyResolutionAction.MATCH }
        val targetIdsBySource = matches.associate { preview ->
            val targetId = preview.effectiveTargetId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Node ${preview.sourceId} has no match target")
            val source = sourceNodesById[preview.sourceId]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Source node ${preview.sourceId} is unavailable")
            val target = targetNodesById[targetId]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Node ${preview.sourceId} has an invalid match target")
            if (source.nodeType.id != target.nodeType.id) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Node ${preview.sourceId} match has a different node type")
            }
            preview.sourceId to targetId
        }
        if (targetIdsBySource.size != matches.size || targetIdsBySource.values.toSet().size != targetIdsBySource.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Node matches must be one-to-one")
        }
        return targetIdsBySource
    }

    private fun resolveLinkMatches(
        previews: List<ru.kavader.arepos.dto.model.DiagramCopyEntityPreview>,
        sourceLinksById: Map<UUID, Links>,
        targetLinksById: Map<UUID, Links>
    ): Map<UUID, UUID> {
        val matches = previews.filter { it.effectiveAction == DiagramCopyResolutionAction.MATCH }
        val targetIdsBySource = matches.associate { preview ->
            val targetId = preview.effectiveTargetId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Link ${preview.sourceId} has no match target")
            val source = sourceLinksById[preview.sourceId]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Source link ${preview.sourceId} is unavailable")
            val target = targetLinksById[targetId]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Link ${preview.sourceId} has an invalid match target")
            if (source.linkType.id != target.linkType.id) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Link ${preview.sourceId} match has a different link type")
            }
            preview.sourceId to targetId
        }
        if (targetIdsBySource.size != matches.size || targetIdsBySource.values.toSet().size != targetIdsBySource.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Link matches must be one-to-one")
        }
        return targetIdsBySource
    }

    private fun validateMatchedLinkEndpoints(
        matchedLinks: Map<UUID, UUID>,
        sourceLinksById: Map<UUID, Links>,
        targetLinksById: Map<UUID, Links>,
        nodeIdMap: Map<UUID, UUID>
    ) {
        matchedLinks.forEach { (sourceLinkId, targetLinkId) ->
            val sourceLink = sourceLinksById.getValue(sourceLinkId)
            val targetLink = targetLinksById.getValue(targetLinkId)
            val expectedSourceId = nodeIdMap[requireNotNull(sourceLink.source.id)]
            val expectedTargetId = nodeIdMap[requireNotNull(sourceLink.target.id)]
            if (
                expectedSourceId != null &&
                expectedTargetId != null &&
                (targetLink.source.id != expectedSourceId || targetLink.target.id != expectedTargetId)
            ) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Link $sourceLinkId match endpoints are inconsistent with resolved nodes"
                )
            }
        }
    }

    private fun collectReferences(attrs: String?): DiagramReferences {
        val root = parseObject(attrs) ?: return DiagramReferences(emptySet(), emptySet(), emptyList())
        val nodeIds = mutableSetOf<UUID>()
        val linkIds = mutableSetOf<UUID>()
        val edges = mutableListOf<DiagramEdgeRef>()
        collectReferencesFromContainer(root, "root", nodeIds, linkIds, edges)
        (root.get("instances") as? ObjectNode)?.let {
            collectReferencesFromContainer(it, "instances", nodeIds, linkIds, edges)
        }
        return DiagramReferences(nodeIds, linkIds, edges)
    }

    private fun collectReferencesFromContainer(
        container: ObjectNode,
        containerName: String,
        nodeIds: MutableSet<UUID>,
        linkIds: MutableSet<UUID>,
        edges: MutableList<DiagramEdgeRef>
    ) {
        (container.get("nodes") as? ArrayNode)?.forEach { node ->
            node.asObject()?.get("modelNodeId")?.asText()?.toUuidOrNull()?.let(nodeIds::add)
        }
        (container.get("edges") as? ArrayNode)?.forEachIndexed { index, edgeNode ->
            val edge = edgeNode.asObject() ?: return@forEachIndexed
            val modelLinkId = edge.get("modelLinkId")?.asText()?.toUuidOrNull()
            val sourceModelNodeId = edge.get("sourceModelNodeId")?.asText()?.toUuidOrNull()
            val targetModelNodeId = edge.get("targetModelNodeId")?.asText()?.toUuidOrNull()
            modelLinkId?.let(linkIds::add)
            sourceModelNodeId?.let(nodeIds::add)
            targetModelNodeId?.let(nodeIds::add)
            if (modelLinkId != null || sourceModelNodeId != null || targetModelNodeId != null) {
                edges += DiagramEdgeRef(
                    edgeInstanceId = edge.get("id")?.asText() ?: "$containerName-edge-$index",
                    modelLinkId = modelLinkId,
                    sourceModelNodeId = sourceModelNodeId,
                    targetModelNodeId = targetModelNodeId
                )
            }
        }
    }

    private fun filterSkippedInstances(
        attrs: String?,
        retainedNodeIds: Set<UUID>,
        retainedLinkIds: Set<UUID>
    ): String? {
        val root = parseObject(attrs) ?: return attrs
        filterContainer(root, retainedNodeIds, retainedLinkIds)
        (root.get("instances") as? ObjectNode)?.let { filterContainer(it, retainedNodeIds, retainedLinkIds) }
        return objectMapper.writeValueAsString(root)
    }

    private fun filterContainer(
        container: ObjectNode,
        retainedNodeIds: Set<UUID>,
        retainedLinkIds: Set<UUID>
    ) {
        filterArray(container, "nodes") { node ->
            node.get("modelNodeId")?.asText()?.toUuidOrNull()?.let { it in retainedNodeIds } ?: true
        }
        filterArray(container, "edges") { edge ->
            val linkIsKept = edge.get("modelLinkId")?.asText()?.toUuidOrNull()?.let { it in retainedLinkIds } ?: true
            val sourceIsKept =
                edge.get("sourceModelNodeId")?.asText()?.toUuidOrNull()?.let { it in retainedNodeIds } ?: true
            val targetIsKept =
                edge.get("targetModelNodeId")?.asText()?.toUuidOrNull()?.let { it in retainedNodeIds } ?: true
            linkIsKept && sourceIsKept && targetIsKept
        }
    }

    private fun filterArray(container: ObjectNode, field: String, keep: (ObjectNode) -> Boolean) {
        val array = container.get(field) as? ArrayNode ?: return
        val kept = objectMapper.createArrayNode()
        array.forEach { item ->
            val obj = item.asObject()
            if (obj == null || keep(obj)) kept.add(item)
        }
        if (kept.size() != array.size()) {
            container.set<ArrayNode>(field, kept)
        }
    }

    private fun suggestVersion(targetModel: Models, sourceDiagram: Diagrams): String {
        val used = diagramsRepository.findByModelIdAndName(
            requireNotNull(targetModel.id),
            sourceDiagram.name
        ).mapTo(mutableSetOf()) { it.version }
        var candidate = sourceDiagram.version
        while (candidate in used) {
            candidate = bumpPatch(candidate)
        }
        return candidate
    }

    private fun diagramNameVersionConflict(name: String, version: String): String =
        "Diagram '$name' version '$version' already exists in the target model"

    private fun bumpPatch(version: String): String {
        val parts = version.split(".")
        if (parts.size != 3) return "$version.1"
        val major = parts[0].toIntOrNull()
        val minor = parts[1].toIntOrNull()
        val patch = parts[2].toIntOrNull()
        if (major == null || minor == null || patch == null) return "$version.1"
        return "$major.$minor.${patch + 1}"
    }

    private fun parseObject(attrs: String?): ObjectNode? = try {
        attrs?.takeIf { it.isNotBlank() }?.let(objectMapper::readTree) as? ObjectNode
    } catch (_: Exception) {
        null
    }

    private fun JsonNode.asObject(): ObjectNode? = this as? ObjectNode

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private fun toMatchableNode(node: Nodes): MatchableNode = MatchableNode(
        id = requireNotNull(node.id),
        stableId = node.stableId,
        name = node.name,
        nodeTypeId = requireNotNull(node.nodeType.id)
    )

    private fun toMatchableLink(link: Links): MatchableLink = MatchableLink(
        id = requireNotNull(link.id),
        stableId = link.stableId,
        linkTypeId = requireNotNull(link.linkType.id),
        sourceNodeId = requireNotNull(link.source.id),
        targetNodeId = requireNotNull(link.target.id)
    )

    private data class DiagramReferences(
        val nodeIds: Set<UUID>,
        val linkIds: Set<UUID>,
        val edges: List<DiagramEdgeRef>
    )

    private data class NotationMapping(
        val componentIds: Map<UUID, UUID>,
        val relationIds: Map<UUID, UUID>,
        val report: DiagramCopyNotationRemapReport,
        val warnings: List<DiagramCopyWarning>
    )

    private data class CopyContext(
        val sourceDiagram: Diagrams,
        val targetModel: Models,
        val targetNotation: ru.kavader.arepos.model.Notations,
        val sourceNodes: List<Nodes>,
        val sourceLinks: List<Links>,
        val targetNodes: List<Nodes>,
        val targetLinks: List<Links>,
        val edgeRefs: List<DiagramEdgeRef>,
        val sourceComponents: List<ru.kavader.arepos.model.Components>,
        val targetComponents: List<ru.kavader.arepos.model.Components>,
        val sourceRelations: List<ru.kavader.arepos.model.Relations>,
        val targetRelations: List<ru.kavader.arepos.model.Relations>
    )
}
