package ru.kavader.arepos.service.diagramcopy

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.service.SystemRootNodeTypeService
import java.time.Instant
import java.util.UUID

@Component
class DiagramCopyFolderAllocator(
    private val nodesRepository: NodesRepository,
    private val systemRootNodeTypeService: SystemRootNodeTypeService,
    private val objectMapper: ObjectMapper
) {

    class Session(
        private val targetModel: Models,
        private val createBaseParent: Nodes,
        private val allSourceNodesById: Map<UUID, Nodes>,
        private val targetNodesById: MutableMap<UUID, Nodes>,
        private val owner: Users,
        private val now: Instant,
        private val occupiedStableIds: MutableSet<UUID>,
        private val directoryNodeType: NodeTypes,
        private val nodesRepository: NodesRepository,
        private val objectMapper: ObjectMapper
    ) {
        private val folderCache = mutableMapOf<UUID, Nodes>()
        val createdFolders = mutableListOf<Nodes>()

        fun parentForCreatedNode(source: Nodes): Nodes {
            val sourceParentId = source.parentNode?.id ?: return createBaseParent
            val sourceParent = allSourceNodesById[sourceParentId] ?: return createBaseParent
            if (isHiddenTreeRoot(sourceParent)) return createBaseParent

            var parent = createBaseParent
            for (folder in directoryPathFromRoot(sourceParent)) {
                parent = ensureFolder(folder, parent)
            }
            return parent
        }

        private fun directoryPathFromRoot(node: Nodes): List<Nodes> {
            val path = mutableListOf<Nodes>()
            var current: Nodes? = node
            while (current != null && !isHiddenTreeRoot(current)) {
                if (isDirectoryNode(current)) {
                    path.add(0, current)
                }
                val parentId = current.parentNode?.id ?: break
                current = allSourceNodesById[parentId]
            }
            return path
        }

        private fun ensureFolder(sourceFolder: Nodes, under: Nodes): Nodes {
            val sourceId = requireNotNull(sourceFolder.id)
            folderCache[sourceId]?.let { return it }

            findMatchingFolder(sourceFolder, under)?.let { existing ->
                folderCache[sourceId] = existing
                return existing
            }

            val stableId = sourceFolder.stableId.takeUnless { it in occupiedStableIds } ?: UUID.randomUUID()
            occupiedStableIds += stableId
            val saved = nodesRepository.save(
                Nodes(
                    stableId = stableId,
                    name = sourceFolder.name,
                    createdAt = now,
                    updatedAt = now,
                    attrs = sourceFolder.attrs,
                    parentNode = under,
                    model = targetModel,
                    owner = owner,
                    nodeType = directoryNodeType
                )
            )
            val savedId = requireNotNull(saved.id)
            targetNodesById[savedId] = saved
            folderCache[sourceId] = saved
            createdFolders += saved
            return saved
        }

        private fun findMatchingFolder(sourceFolder: Nodes, under: Nodes): Nodes? {
            val underId = requireNotNull(under.id)
            val siblings = targetNodesById.values.filter { it.parentNode?.id == underId }
            siblings.firstOrNull { it.stableId == sourceFolder.stableId }?.let { return it }
            val directorySiblings = siblings.filter(::isDirectoryNode)
            val exactNameMatches = directorySiblings.filter { it.name == sourceFolder.name }
            if (exactNameMatches.size == 1) return exactNameMatches.single()
            return directorySiblings.singleOrNull {
                it.name.equals(sourceFolder.name, ignoreCase = true)
            }
        }

        private fun isDirectoryNode(node: Nodes): Boolean =
            node.nodeType.name.equals(DIRECTORY_NODE_TYPE_NAME, ignoreCase = true)

        private fun isHiddenTreeRoot(node: Nodes): Boolean {
            val attrs = node.attrs ?: return false
            return try {
                val root = objectMapper.readTree(attrs)
                root.path("system").path("hiddenTreeRoot").asBoolean(false)
            } catch (_: Exception) {
                false
            }
        }
    }

    fun openSession(
        targetModel: Models,
        createBaseParent: Nodes,
        allSourceNodesById: Map<UUID, Nodes>,
        targetNodesById: MutableMap<UUID, Nodes>,
        owner: Users,
        now: Instant,
        occupiedStableIds: MutableSet<UUID>
    ): Session {
        val directoryNodeType = systemRootNodeTypeService.getOrCreate(owner, now)
        return Session(
            targetModel = targetModel,
            createBaseParent = createBaseParent,
            allSourceNodesById = allSourceNodesById,
            targetNodesById = targetNodesById,
            owner = owner,
            now = now,
            occupiedStableIds = occupiedStableIds,
            directoryNodeType = directoryNodeType,
            nodesRepository = nodesRepository,
            objectMapper = objectMapper
        )
    }

    companion object {
        private const val DIRECTORY_NODE_TYPE_NAME = "Directory"
    }
}
