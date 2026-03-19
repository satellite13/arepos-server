package ru.kavader.arepos.service

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import java.util.UUID

@Service
class ModelDiffService(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository
) {

    fun computeDiff(baseModelId: UUID, targetModelId: UUID): ModelDiffResponse {
        val baseModel = modelsRepository.findById(baseModelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Base model $baseModelId not found")
        }
        val targetModel = modelsRepository.findById(targetModelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Target model $targetModelId not found")
        }

        val baseNodes = nodesRepository.findByModelIdOrdered(baseModelId, Pageable.unpaged()).content
        val targetNodes = nodesRepository.findByModelIdOrdered(targetModelId, Pageable.unpaged()).content

        val baseLinks = linksRepository.findByModel(baseModel, Pageable.unpaged()).content
        val targetLinks = linksRepository.findByModel(targetModel, Pageable.unpaged()).content

        val baseDiagrams = diagramsRepository.findByFilters(
            ownerId = null, modelId = baseModelId, nodeId = null, notationId = null,
            name = "", pageable = Pageable.unpaged()
        ).content
        val targetDiagrams = diagramsRepository.findByFilters(
            ownerId = null, modelId = targetModelId, nodeId = null, notationId = null,
            name = "", pageable = Pageable.unpaged()
        ).content

        val basePathMap = buildNodePathMap(baseNodes)
        val targetPathMap = buildNodePathMap(targetNodes)

        return ModelDiffResponse(
            nodes = compareNodes(baseNodes, targetNodes, basePathMap, targetPathMap),
            links = compareLinks(baseLinks, targetLinks, basePathMap, targetPathMap),
            diagrams = compareDiagrams(baseDiagrams, targetDiagrams)
        )
    }

    internal fun buildNodePathMap(nodes: List<Nodes>): Map<UUID, String> {
        val nodeById = nodes.associateBy { it.id!! }
        val cache = mutableMapOf<UUID, String>()

        fun resolvePath(nodeId: UUID): String {
            cache[nodeId]?.let { return it }
            val node = nodeById[nodeId] ?: return "?"
            val parentId = node.parentNode?.id
            val path = if (parentId != null && nodeById.containsKey(parentId)) {
                "${resolvePath(parentId)}/${node.name}"
            } else {
                node.name
            }
            cache[nodeId] = path
            return path
        }

        nodes.forEach { resolvePath(it.id!!) }
        return cache
    }

    internal fun compareNodes(
        baseNodes: List<Nodes>,
        targetNodes: List<Nodes>,
        basePathMap: Map<UUID, String>,
        targetPathMap: Map<UUID, String>
    ): List<NodeDiffItem> {
        val result = mutableListOf<NodeDiffItem>()
        val matchedTargetIds = mutableSetOf<UUID>()
        val matchedBaseIds = mutableSetOf<UUID>()

        val targetByStableId = targetNodes.associateBy { it.stableId }

        for (baseNode in baseNodes) {
            val targetNode = targetByStableId[baseNode.stableId]
            if (targetNode != null && targetNode.id!! !in matchedTargetIds) {
                matchedBaseIds.add(baseNode.id!!)
                matchedTargetIds.add(targetNode.id!!)
                if (!nodeEquals(baseNode, targetNode)) {
                    result.add(
                        NodeDiffItem.Modified(
                            path = targetPathMap[targetNode.id!!] ?: "",
                            base = baseNode.toDiffNode(),
                            target = targetNode.toDiffNode()
                        )
                    )
                }
            }
        }

        val unmatchedTargetByPath = targetNodes
            .filter { it.id!! !in matchedTargetIds }
            .associateBy { targetPathMap[it.id!!] ?: "" }

        for (baseNode in baseNodes) {
            if (baseNode.id!! in matchedBaseIds) continue
            val basePath = basePathMap[baseNode.id!!] ?: ""
            val targetNode = unmatchedTargetByPath[basePath]
            if (targetNode != null) {
                matchedBaseIds.add(baseNode.id!!)
                matchedTargetIds.add(targetNode.id!!)
                if (!nodeEquals(baseNode, targetNode)) {
                    result.add(
                        NodeDiffItem.Modified(
                            path = targetPathMap[targetNode.id!!] ?: "",
                            base = baseNode.toDiffNode(),
                            target = targetNode.toDiffNode()
                        )
                    )
                }
            } else {
                result.add(NodeDiffItem.Removed(path = basePath, node = baseNode.toDiffNode()))
            }
        }

        for (targetNode in targetNodes) {
            if (targetNode.id!! !in matchedTargetIds) {
                result.add(
                    NodeDiffItem.Added(
                        path = targetPathMap[targetNode.id!!] ?: "",
                        node = targetNode.toDiffNode()
                    )
                )
            }
        }

        return result
    }

    internal fun compareLinks(
        baseLinks: List<Links>,
        targetLinks: List<Links>,
        basePathMap: Map<UUID, String>,
        targetPathMap: Map<UUID, String>
    ): List<LinkDiffItem> {
        val result = mutableListOf<LinkDiffItem>()
        val matchedTargetIds = mutableSetOf<UUID>()
        val matchedBaseIds = mutableSetOf<UUID>()

        val targetByStableId = targetLinks.associateBy { it.stableId }

        for (baseLink in baseLinks) {
            val targetLink = targetByStableId[baseLink.stableId]
            if (targetLink != null && targetLink.id!! !in matchedTargetIds) {
                matchedBaseIds.add(baseLink.id!!)
                matchedTargetIds.add(targetLink.id!!)
                if (!linkEquals(baseLink, targetLink)) {
                    result.add(
                        LinkDiffItem.Modified(
                            key = linkKey(targetLink, targetPathMap),
                            base = baseLink.toDiffLink(basePathMap),
                            target = targetLink.toDiffLink(targetPathMap)
                        )
                    )
                }
            }
        }

        val unmatchedTargetByKey = targetLinks
            .filter { it.id!! !in matchedTargetIds }
            .associateBy { linkKey(it, targetPathMap) }

        for (baseLink in baseLinks) {
            if (baseLink.id!! in matchedBaseIds) continue
            val baseKey = linkKey(baseLink, basePathMap)
            val targetLink = unmatchedTargetByKey[baseKey]
            if (targetLink != null) {
                matchedBaseIds.add(baseLink.id!!)
                matchedTargetIds.add(targetLink.id!!)
                if (!linkEquals(baseLink, targetLink)) {
                    result.add(
                        LinkDiffItem.Modified(
                            key = linkKey(targetLink, targetPathMap),
                            base = baseLink.toDiffLink(basePathMap),
                            target = targetLink.toDiffLink(targetPathMap)
                        )
                    )
                }
            } else {
                result.add(LinkDiffItem.Removed(key = baseKey, link = baseLink.toDiffLink(basePathMap)))
            }
        }

        for (targetLink in targetLinks) {
            if (targetLink.id!! !in matchedTargetIds) {
                result.add(
                    LinkDiffItem.Added(
                        key = linkKey(targetLink, targetPathMap),
                        link = targetLink.toDiffLink(targetPathMap)
                    )
                )
            }
        }

        return result
    }

    internal fun compareDiagrams(
        baseDiagrams: List<Diagrams>,
        targetDiagrams: List<Diagrams>
    ): List<DiagramDiffItem> {
        val result = mutableListOf<DiagramDiffItem>()
        val baseDeduped = deduplicateByName(baseDiagrams)
        val targetDeduped = deduplicateByName(targetDiagrams)

        val targetByName = targetDeduped.associateBy { it.name.trim() }
        val matchedTargetNames = mutableSetOf<String>()

        for (baseDiagram in baseDeduped) {
            val key = baseDiagram.name.trim()
            val targetDiagram = targetByName[key]
            if (targetDiagram != null) {
                matchedTargetNames.add(key)
                if (!diagramEquals(baseDiagram, targetDiagram)) {
                    result.add(
                        DiagramDiffItem.Modified(
                            name = key,
                            base = baseDiagram.toDiffDiagram(),
                            target = targetDiagram.toDiffDiagram()
                        )
                    )
                }
            } else {
                result.add(DiagramDiffItem.Removed(name = key, diagram = baseDiagram.toDiffDiagram()))
            }
        }

        for (targetDiagram in targetDeduped) {
            val key = targetDiagram.name.trim()
            if (key !in matchedTargetNames) {
                result.add(DiagramDiffItem.Added(name = key, diagram = targetDiagram.toDiffDiagram()))
            }
        }

        return result
    }

    private fun nodeEquals(a: Nodes, b: Nodes): Boolean =
        a.name == b.name &&
            (a.attrs ?: "") == (b.attrs ?: "") &&
            a.nodeType.id == b.nodeType.id

    private fun linkEquals(a: Links, b: Links): Boolean =
        (a.attrs ?: "") == (b.attrs ?: "") &&
            a.linkType.id == b.linkType.id

    private fun diagramEquals(a: Diagrams, b: Diagrams): Boolean =
        a.version == b.version &&
            (a.attrs ?: "") == (b.attrs ?: "") &&
            a.notation.id == b.notation.id

    private fun linkKey(link: Links, pathMap: Map<UUID, String>): String {
        val sourcePath = pathMap[link.source.id!!] ?: "?"
        val targetPath = pathMap[link.target.id!!] ?: "?"
        return "$sourcePath -> $targetPath [${link.linkType.id}]"
    }

    private fun deduplicateByName(diagrams: List<Diagrams>): List<Diagrams> =
        diagrams
            .groupBy { it.name.trim() }
            .values
            .map { group ->
                group.maxWithOrNull(Comparator { a, b -> compareVersions(a.version, b.version) })
                    ?: group.first()
            }

    private fun Nodes.toDiffNode() = NodeDiffNode(
        id = id!!,
        stableId = stableId,
        name = name,
        nodeTypeId = nodeType.id!!,
        parentNodeId = parentNode?.id,
        attrs = attrs
    )

    private fun Links.toDiffLink(pathMap: Map<UUID, String>) = LinkDiffLink(
        id = id!!,
        stableId = stableId,
        sourceNodeId = source.id!!,
        targetNodeId = target.id!!,
        sourcePath = pathMap[source.id!!] ?: "?",
        targetPath = pathMap[target.id!!] ?: "?",
        linkTypeId = linkType.id!!,
        attrs = attrs
    )

    private fun Diagrams.toDiffDiagram() = DiagramDiffDiagram(
        id = id!!,
        name = name,
        version = version,
        notationId = notation.id!!,
        attrs = attrs
    )

    companion object {
        fun compareVersions(a: String, b: String): Int {
            val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
            val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(aParts.size, bParts.size)) {
                val av = aParts.getOrElse(i) { 0 }
                val bv = bParts.getOrElse(i) { 0 }
                if (av != bv) return av.compareTo(bv)
            }
            return 0
        }
    }
}

data class ModelDiffResponse(
    val nodes: List<NodeDiffItem>,
    val links: List<LinkDiffItem>,
    val diagrams: List<DiagramDiffItem>
)

sealed class NodeDiffItem {
    abstract val kind: String
    abstract val path: String

    data class Added(override val path: String, val node: NodeDiffNode) : NodeDiffItem() {
        override val kind = "added"
    }

    data class Removed(override val path: String, val node: NodeDiffNode) : NodeDiffItem() {
        override val kind = "removed"
    }

    data class Modified(override val path: String, val base: NodeDiffNode, val target: NodeDiffNode) : NodeDiffItem() {
        override val kind = "modified"
    }
}

data class NodeDiffNode(
    val id: UUID,
    val stableId: UUID?,
    val name: String,
    val nodeTypeId: UUID,
    val parentNodeId: UUID?,
    val attrs: String?
)

sealed class LinkDiffItem {
    abstract val kind: String
    abstract val key: String

    data class Added(override val key: String, val link: LinkDiffLink) : LinkDiffItem() {
        override val kind = "added"
    }

    data class Removed(override val key: String, val link: LinkDiffLink) : LinkDiffItem() {
        override val kind = "removed"
    }

    data class Modified(override val key: String, val base: LinkDiffLink, val target: LinkDiffLink) : LinkDiffItem() {
        override val kind = "modified"
    }
}

data class LinkDiffLink(
    val id: UUID,
    val stableId: UUID?,
    val sourceNodeId: UUID,
    val targetNodeId: UUID,
    val sourcePath: String,
    val targetPath: String,
    val linkTypeId: UUID,
    val attrs: String?
)

sealed class DiagramDiffItem {
    abstract val kind: String
    abstract val name: String

    data class Added(override val name: String, val diagram: DiagramDiffDiagram) : DiagramDiffItem() {
        override val kind = "added"
    }

    data class Removed(override val name: String, val diagram: DiagramDiffDiagram) : DiagramDiffItem() {
        override val kind = "removed"
    }

    data class Modified(
        override val name: String,
        val base: DiagramDiffDiagram,
        val target: DiagramDiffDiagram
    ) : DiagramDiffItem() {
        override val kind = "modified"
    }
}

data class DiagramDiffDiagram(
    val id: UUID,
    val name: String,
    val version: String,
    val notationId: UUID,
    val attrs: String?
)
