package ru.kavader.arepos.service.diagramcopy

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.model.DiagramCopyCandidate
import ru.kavader.arepos.dto.model.DiagramCopyEdgeBlocker
import ru.kavader.arepos.dto.model.DiagramCopyEntityKind
import ru.kavader.arepos.dto.model.DiagramCopyEntityPreview
import ru.kavader.arepos.dto.model.DiagramCopyMatchReason
import ru.kavader.arepos.dto.model.DiagramCopyResolution
import ru.kavader.arepos.dto.model.DiagramCopyResolutionAction
import java.util.UUID

data class MatchableNode(
    val id: UUID,
    val stableId: UUID,
    val name: String,
    val nodeTypeId: UUID,
    val deleted: Boolean = false
)

data class MatchableLink(
    val id: UUID,
    val stableId: UUID,
    val linkTypeId: UUID,
    val sourceNodeId: UUID,
    val targetNodeId: UUID,
    val deleted: Boolean = false
)

data class DiagramEdgeRef(
    val edgeInstanceId: String,
    val modelLinkId: UUID?,
    val sourceModelNodeId: UUID?,
    val targetModelNodeId: UUID?
)

data class MatcherResult(
    val nodes: List<DiagramCopyEntityPreview>,
    val links: List<DiagramCopyEntityPreview>,
    val blockers: List<DiagramCopyEdgeBlocker>,
    val canCommit: Boolean
)

@Component
class DiagramCopyMatcher {

    companion object {
        const val UNRESOLVED_EDGE_ENDPOINT = "UNRESOLVED_EDGE_ENDPOINT"
    }

    fun buildPreview(
        sourceNodes: List<MatchableNode>,
        sourceLinks: List<MatchableLink>,
        targetNodes: List<MatchableNode>,
        targetLinks: List<MatchableLink>,
        edges: List<DiagramEdgeRef>,
        resolutions: List<DiagramCopyResolution>
    ): MatcherResult {
        val sourceNodesById = sourceNodes.associateBy { it.id }
        val sourceLinksById = sourceLinks.associateBy { it.id }
        val targetNodesById = targetNodes.filterNot { it.deleted }.associateBy { it.id }
        val targetLinksById = targetLinks.filterNot { it.deleted }.associateBy { it.id }
        val resolutionsByEntity = resolutions.associateBy { it.kind to it.sourceId }
        val nodeEndpointIds = edgeEndpointIds(edges, sourceLinksById)

        val nodeMatches = autoMatchNodes(sourceNodes, targetNodesById.values.toList())
        val nodePreviews = sourceNodes
            .sortedBy { it.id.toString() }
            .map { source ->
                val match = nodeMatches[source.id]
                previewForNode(
                    source = source,
                    match = match,
                    resolution = resolutionsByEntity[DiagramCopyEntityKind.NODE to source.id],
                    isEndpointOfEdge = source.id in nodeEndpointIds
                )
            }

        val linkMatches = autoMatchLinks(
            sourceLinks = sourceLinks,
            targetLinks = targetLinksById.values.toList(),
            nodeMatches = nodeMatches,
            nodeResolutions = resolutionsByEntity
                .filterKeys { (kind, _) -> kind == DiagramCopyEntityKind.NODE }
                .mapKeys { (key, _) -> key.second }
        )
        val linkPreviews = sourceLinks
            .sortedBy { it.id.toString() }
            .map { source ->
                val match = linkMatches[source.id]
                previewForLink(
                    source = source,
                    match = match,
                    resolution = resolutionsByEntity[DiagramCopyEntityKind.LINK to source.id],
                    sourceNodesById = sourceNodesById,
                    targetNodesById = targetNodesById
                )
            }

        val previewsByEntity = (nodePreviews + linkPreviews).associateBy { it.kind to it.sourceId }
        val blockers = edgeBlockers(edges, sourceLinksById, previewsByEntity)
        val referencedEntities = sourceNodes.mapTo(mutableSetOf()) {
            DiagramCopyEntityKind.NODE to it.id
        }.apply {
            sourceLinks.forEach { add(DiagramCopyEntityKind.LINK to it.id) }
        }
        val everyReferencedEntityResolved = referencedEntities.all { entity ->
            previewsByEntity[entity]?.hasEffectiveAction() == true
        }

        return MatcherResult(
            nodes = nodePreviews,
            links = linkPreviews,
            blockers = blockers,
            canCommit = blockers.isEmpty() && everyReferencedEntityResolved
        )
    }

    private fun autoMatchNodes(
        sourceNodes: List<MatchableNode>,
        targetNodes: List<MatchableNode>
    ): Map<UUID, AutoMatch<MatchableNode>> {
        val claimedTargetIds = mutableSetOf<UUID>()

        return sourceNodes
            .sortedBy { it.id.toString() }
            .associate { source ->
                val stableIdCandidates = targetNodes.filter { it.stableId == source.stableId }
                val candidates = if (stableIdCandidates.isNotEmpty()) {
                    stableIdCandidates to DiagramCopyMatchReason.STABLE_ID
                } else {
                    targetNodes
                        .filter { it.name == source.name && it.nodeTypeId == source.nodeTypeId } to
                        DiagramCopyMatchReason.NAME_AND_TYPE
                }
                val target = candidates.first.singleOrNull()
                if (target != null && claimedTargetIds.add(target.id)) {
                    source.id to AutoMatch(target, candidates.second, candidates.first)
                } else {
                    source.id to AutoMatch(null, candidates.second, candidates.first)
                }
            }
    }

    private fun autoMatchLinks(
        sourceLinks: List<MatchableLink>,
        targetLinks: List<MatchableLink>,
        nodeMatches: Map<UUID, AutoMatch<MatchableNode>>,
        nodeResolutions: Map<UUID, DiagramCopyResolution>
    ): Map<UUID, AutoMatch<MatchableLink>> {
        val claimedTargetIds = mutableSetOf<UUID>()

        return sourceLinks
            .sortedBy { it.id.toString() }
            .associate { source ->
                val stableIdCandidates = targetLinks.filter { it.stableId == source.stableId }
                val candidates = if (stableIdCandidates.isNotEmpty()) {
                    stableIdCandidates to DiagramCopyMatchReason.STABLE_ID
                } else {
                    val matchedSourceId = effectiveNodeTargetId(
                        source.sourceNodeId,
                        nodeMatches,
                        nodeResolutions
                    )
                    val matchedTargetId = effectiveNodeTargetId(
                        source.targetNodeId,
                        nodeMatches,
                        nodeResolutions
                    )
                    if (matchedSourceId != null && matchedTargetId != null) {
                        targetLinks.filter {
                            it.linkTypeId == source.linkTypeId &&
                                it.sourceNodeId == matchedSourceId &&
                                it.targetNodeId == matchedTargetId
                        } to DiagramCopyMatchReason.ENDPOINTS_AND_TYPE
                    } else {
                        emptyList<MatchableLink>() to DiagramCopyMatchReason.ENDPOINTS_AND_TYPE
                    }
                }
                val target = candidates.first.singleOrNull()
                if (target != null && claimedTargetIds.add(target.id)) {
                    source.id to AutoMatch(target, candidates.second, candidates.first)
                } else {
                    source.id to AutoMatch(null, candidates.second, candidates.first)
                }
            }
    }

    private fun effectiveNodeTargetId(
        sourceNodeId: UUID,
        nodeMatches: Map<UUID, AutoMatch<MatchableNode>>,
        nodeResolutions: Map<UUID, DiagramCopyResolution>
    ): UUID? {
        val resolution = nodeResolutions[sourceNodeId]
        return if (resolution?.action == DiagramCopyResolutionAction.MATCH) {
            resolution.targetId
        } else {
            nodeMatches[sourceNodeId]?.target?.id
        }
    }

    private fun previewForNode(
        source: MatchableNode,
        match: AutoMatch<MatchableNode>?,
        resolution: DiagramCopyResolution?,
        isEndpointOfEdge: Boolean
    ): DiagramCopyEntityPreview {
        val effective = effectiveResolution(match?.target?.id, resolution)
        return DiagramCopyEntityPreview(
            sourceId = source.id,
            kind = DiagramCopyEntityKind.NODE,
            label = source.name,
            stableId = source.stableId,
            typeId = source.nodeTypeId,
            autoMatchTargetId = match?.target?.id,
            autoMatchReason = match?.target?.let { match.reason },
            candidates = match?.takeIf { it.target == null }?.candidates?.map(::nodeCandidate).orEmpty(),
            effectiveAction = effective.action,
            effectiveTargetId = effective.targetId,
            isEndpointOfEdge = isEndpointOfEdge
        )
    }

    private fun previewForLink(
        source: MatchableLink,
        match: AutoMatch<MatchableLink>?,
        resolution: DiagramCopyResolution?,
        sourceNodesById: Map<UUID, MatchableNode>,
        targetNodesById: Map<UUID, MatchableNode>
    ): DiagramCopyEntityPreview {
        val effective = effectiveResolution(match?.target?.id, resolution)
        return DiagramCopyEntityPreview(
            sourceId = source.id,
            kind = DiagramCopyEntityKind.LINK,
            label = linkLabel(source, sourceNodesById),
            stableId = source.stableId,
            typeId = source.linkTypeId,
            autoMatchTargetId = match?.target?.id,
            autoMatchReason = match?.target?.let { match.reason },
            candidates = match?.takeIf { it.target == null }
                ?.candidates
                ?.map { linkCandidate(it, targetNodesById) }
                .orEmpty(),
            effectiveAction = effective.action,
            effectiveTargetId = effective.targetId
        )
    }

    private fun effectiveResolution(
        autoMatchTargetId: UUID?,
        resolution: DiagramCopyResolution?
    ): EffectiveResolution = when {
        resolution == null && autoMatchTargetId != null ->
            EffectiveResolution(DiagramCopyResolutionAction.MATCH, autoMatchTargetId)

        resolution == null -> EffectiveResolution(null, null)
        resolution.action == DiagramCopyResolutionAction.MATCH ->
            EffectiveResolution(resolution.action, resolution.targetId)

        else -> EffectiveResolution(resolution.action, null)
    }

    private fun edgeEndpointIds(
        edges: List<DiagramEdgeRef>,
        sourceLinksById: Map<UUID, MatchableLink>
    ): Set<UUID> = edges.flatMapTo(mutableSetOf()) { edge ->
        buildList {
            edge.sourceModelNodeId?.let(::add)
            edge.targetModelNodeId?.let(::add)
            sourceLinksById[edge.modelLinkId]?.let { link ->
                add(link.sourceNodeId)
                add(link.targetNodeId)
            }
        }
    }

    private fun edgeBlockers(
        edges: List<DiagramEdgeRef>,
        sourceLinksById: Map<UUID, MatchableLink>,
        previewsByEntity: Map<Pair<DiagramCopyEntityKind, UUID>, DiagramCopyEntityPreview>
    ): List<DiagramCopyEdgeBlocker> = edges.mapNotNull { edge ->
        val requiredEntities = buildSet {
            edgeEndpointIds(listOf(edge), sourceLinksById).forEach { nodeId ->
                add(DiagramCopyEntityKind.NODE to nodeId)
            }
            edge.modelLinkId?.let { linkId ->
                add(DiagramCopyEntityKind.LINK to linkId)
            }
        }
        val hasUnresolvedEntity = requiredEntities.any { entity ->
            previewsByEntity[entity]?.isReadyForEdge() != true
        }

        if (hasUnresolvedEntity) {
            DiagramCopyEdgeBlocker(
                edgeInstanceId = edge.edgeInstanceId,
                modelLinkId = edge.modelLinkId,
                sourceModelNodeId = edge.sourceModelNodeId,
                targetModelNodeId = edge.targetModelNodeId,
                code = UNRESOLVED_EDGE_ENDPOINT,
                reason = "An edge endpoint or link is unresolved"
            )
        } else {
            null
        }
    }

    private fun nodeCandidate(node: MatchableNode): DiagramCopyCandidate = DiagramCopyCandidate(
        id = node.id,
        label = node.name,
        stableId = node.stableId,
        typeId = node.nodeTypeId
    )

    private fun linkCandidate(
        link: MatchableLink,
        nodesById: Map<UUID, MatchableNode>
    ): DiagramCopyCandidate = DiagramCopyCandidate(
        id = link.id,
        label = linkLabel(link, nodesById),
        stableId = link.stableId,
        typeId = link.linkTypeId
    )

    private fun linkLabel(link: MatchableLink, nodesById: Map<UUID, MatchableNode>): String {
        val sourceName = nodesById[link.sourceNodeId]?.name ?: link.sourceNodeId.toString()
        val targetName = nodesById[link.targetNodeId]?.name ?: link.targetNodeId.toString()
        return "$sourceName → $targetName"
    }

    private fun DiagramCopyEntityPreview.hasEffectiveAction(): Boolean =
        effectiveAction != null &&
            (effectiveAction != DiagramCopyResolutionAction.MATCH || effectiveTargetId != null)

    private fun DiagramCopyEntityPreview.isReadyForEdge(): Boolean =
        effectiveAction == DiagramCopyResolutionAction.CREATE ||
            (effectiveAction == DiagramCopyResolutionAction.MATCH && effectiveTargetId != null)

    private data class AutoMatch<T>(
        val target: T?,
        val reason: DiagramCopyMatchReason,
        val candidates: List<T>
    )

    private data class EffectiveResolution(
        val action: DiagramCopyResolutionAction?,
        val targetId: UUID?
    )
}
