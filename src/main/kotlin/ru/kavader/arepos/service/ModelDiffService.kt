package ru.kavader.arepos.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
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
    companion object {
        private const val DIFF_PAGE_SIZE = 1000
        private const val MAX_NODES_PER_MODEL = 20_000
        private const val MAX_LINKS_PER_MODEL = 20_000
        private const val MAX_DIAGRAMS_PER_MODEL = 5_000
        private const val MAX_NODE_PATH_DEPTH = 256

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

    fun computeDiff(baseModelId: UUID, targetModelId: UUID): ModelDiffResponse {
        val baseModel = modelsRepository.findById(baseModelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Base model $baseModelId not found")
        }
        val targetModel = modelsRepository.findById(targetModelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Target model $targetModelId not found")
        }

        val baseNodes = loadAllPages(MAX_NODES_PER_MODEL, "nodes", baseModelId) { pageable ->
            nodesRepository.findByModelIdOrdered(baseModelId, pageable)
        }
        val targetNodes = loadAllPages(MAX_NODES_PER_MODEL, "nodes", targetModelId) { pageable ->
            nodesRepository.findByModelIdOrdered(targetModelId, pageable)
        }

        val baseLinks = loadAllPages(MAX_LINKS_PER_MODEL, "links", baseModelId) { pageable ->
            linksRepository.findByModel(baseModel, pageable)
        }
        val targetLinks = loadAllPages(MAX_LINKS_PER_MODEL, "links", targetModelId) { pageable ->
            linksRepository.findByModel(targetModel, pageable)
        }

        val baseDiagrams = loadAllPages(MAX_DIAGRAMS_PER_MODEL, "diagrams", baseModelId) { pageable ->
            diagramsRepository.findByFilters(
                ownerId = null,
                modelId = baseModelId,
                nodeId = null,
                notationId = null,
                name = "",
                pageable = pageable
            )
        }
        val targetDiagrams = loadAllPages(MAX_DIAGRAMS_PER_MODEL, "diagrams", targetModelId) { pageable ->
            diagramsRepository.findByFilters(
                ownerId = null,
                modelId = targetModelId,
                nodeId = null,
                notationId = null,
                name = "",
                pageable = pageable
            )
        }

        val basePathMap = buildNodePathMap(baseNodes)
        val targetPathMap = buildNodePathMap(targetNodes)

        return ModelDiffResponse(
            nodes = compareNodes(baseNodes, targetNodes, basePathMap, targetPathMap),
            links = compareLinks(baseLinks, targetLinks, basePathMap, targetPathMap),
            diagrams = compareDiagrams(baseDiagrams, targetDiagrams)
        )
    }

    internal fun buildNodePathMap(nodes: List<Nodes>): Map<UUID, String> {
        val nodeById = nodes.associateBy { requireNotNull(it.id) }
        val childrenByParent = mutableMapOf<UUID, MutableList<Nodes>>()
        val roots = mutableListOf<Nodes>()

        for (node in nodes) {
            val parentId = node.parentNode?.id
            if (parentId == null || !nodeById.containsKey(parentId)) {
                roots.add(node)
            } else {
                childrenByParent.computeIfAbsent(parentId) { mutableListOf() }.add(node)
            }
        }

        val pathMap = mutableMapOf<UUID, String>()
        val depthMap = mutableMapOf<UUID, Int>()
        val queue = ArrayDeque<UUID>()

        for (root in roots) {
            val rootId = requireNotNull(root.id)
            pathMap[rootId] = root.name
            depthMap[rootId] = 1
            queue.addLast(rootId)
        }

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            val currentPath = pathMap[currentId] ?: continue
            val currentDepth = depthMap[currentId] ?: 1
            val children = childrenByParent[currentId].orEmpty()
            for (child in children) {
                val childId = requireNotNull(child.id)
                if (childId in pathMap) continue
                val childDepth = currentDepth + 1
                if (childDepth > MAX_NODE_PATH_DEPTH) {
                    throw ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Model tree depth exceeds supported limit ($MAX_NODE_PATH_DEPTH)"
                    )
                }
                pathMap[childId] = "$currentPath/${child.name}"
                depthMap[childId] = childDepth
                queue.addLast(childId)
            }
        }

        if (pathMap.size != nodes.size) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Model tree contains a cycle or disconnected parent chain"
            )
        }

        return pathMap
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

    private fun <T> loadAllPages(
        maxItems: Int,
        entityName: String,
        modelId: UUID,
        fetchPage: (Pageable) -> Page<T>
    ): List<T> {
        val all = mutableListOf<T>()
        var pageNumber = 0
        while (true) {
            val page = fetchPage(PageRequest.of(pageNumber, DIFF_PAGE_SIZE))
            all.addAll(page.content)
            if (all.size > maxItems) {
                throw ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Diff for model $modelId exceeds $maxItems $entityName, reduce model size"
                )
            }
            if (!page.hasNext()) break
            pageNumber++
        }
        return all
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
