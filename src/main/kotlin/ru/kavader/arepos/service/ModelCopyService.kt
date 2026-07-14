package ru.kavader.arepos.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import java.time.Instant
import java.util.*

@Service
class ModelCopyService(
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val systemRootNodeTypeService: SystemRootNodeTypeService,
    private val modelAttrsService: ModelAttrsService,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(ModelCopyService::class.java)
        private const val SYSTEM_ROOT_NODE_NAME = "Root"
    }

    @Transactional
    fun copyModel(source: Models, owner: Users, name: String, version: String): Models {
        val now = Instant.now()
        val newModel = modelsRepository.save(
            Models(
                name = name,
                createdAt = now,
                updatedAt = now,
                attrs = source.attrs,
                version = version,
                owner = owner,
                source = source,
                deleted = false
            )
        )

        val rootNodeType = systemRootNodeTypeService.getOrCreate(owner, now)
        val sourceNodes = nodesRepository.findByModelIdOrdered(source.id!!, Pageable.unpaged()).content
        val nodeIdMap = mutableMapOf<UUID, UUID>()
        val copiedNodesBySourceId = mutableMapOf<UUID, Nodes>()
        val sourceRoot = sourceNodes.firstOrNull { it.parentNode == null }
            ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Source model has no root node")

        val newRoot = nodesRepository.save(
            Nodes(
                stableId = sourceRoot.stableId,
                name = SYSTEM_ROOT_NODE_NAME,
                createdAt = now,
                updatedAt = now,
                attrs = sourceRoot.attrs,
                parentNode = null,
                model = newModel,
                owner = owner,
                nodeType = rootNodeType
            )
        )
        val sourceRootId = requireNotNull(sourceRoot.id)
        nodeIdMap[sourceRootId] = requireNotNull(newRoot.id)
        copiedNodesBySourceId[sourceRootId] = newRoot
        val attrsWithRoot = modelAttrsService.mergeWithTreeRootNodeId(newModel.attrs, newRoot.id!!)
        newModel.attrs = attrsWithRoot
        modelsRepository.save(newModel)

        val pendingNodes = sourceNodes.filter { it.id != sourceRoot.id }.toMutableList()
        while (pendingNodes.isNotEmpty()) {
            var progress = false
            val iterator = pendingNodes.iterator()
            while (iterator.hasNext()) {
                val srcNode = iterator.next()
                val sourceParentId = srcNode.parentNode?.id ?: continue
                val newParent = copiedNodesBySourceId[sourceParentId] ?: continue
                val saved = nodesRepository.save(
                    Nodes(
                        stableId = srcNode.stableId,
                        name = srcNode.name,
                        createdAt = now,
                        updatedAt = now,
                        attrs = srcNode.attrs,
                        parentNode = newParent,
                        model = newModel,
                        owner = owner,
                        nodeType = srcNode.nodeType
                    )
                )
                val sourceNodeId = requireNotNull(srcNode.id)
                nodeIdMap[sourceNodeId] = requireNotNull(saved.id)
                copiedNodesBySourceId[sourceNodeId] = saved
                iterator.remove()
                progress = true
            }
            if (!progress) {
                val unmapped = pendingNodes.firstOrNull()
                throw IllegalStateException(
                    "Parent node not yet mapped for node ${unmapped?.id}. " +
                            "Check that all nodes have a path to root (no cycles or broken parent links)."
                )
            }
        }

        val sourceLinks = linksRepository.findByModel(source, Pageable.unpaged()).content
        val linkIdMap = mutableMapOf<UUID, UUID>()
        for (srcLink in sourceLinks) {
            val sourceNodeId = requireNotNull(srcLink.source.id)
            val targetNodeId = requireNotNull(srcLink.target.id)
            val newSource = copiedNodesBySourceId[sourceNodeId] ?: continue
            val newTarget = copiedNodesBySourceId[targetNodeId] ?: continue
            val copiedLink = linksRepository.save(
                Links(
                    stableId = srcLink.stableId,
                    source = newSource,
                    target = newTarget,
                    attrs = srcLink.attrs,
                    createdAt = now,
                    updatedAt = now,
                    owner = owner,
                    linkType = srcLink.linkType,
                    model = newModel
                )
            )
            linkIdMap[srcLink.id!!] = copiedLink.id!!
        }

        val sourceDiagrams = diagramsRepository.findByFilters(
            ownerId = null,
            modelId = source.id,
            nodeId = null,
            notationId = null,
            name = "",
            pageable = Pageable.unpaged()
        ).content
        for (srcDiagram in sourceDiagrams) {
            val newNode = srcDiagram.node?.id?.let { sourceNodeId -> copiedNodesBySourceId[sourceNodeId] }
            val remappedAttrs = remapDiagramAttrs(srcDiagram.attrs, nodeIdMap, linkIdMap)
            diagramsRepository.save(
                Diagrams(
                    name = srcDiagram.name,
                    version = srcDiagram.version,
                    createdAt = now,
                    updatedAt = now,
                    attrs = remappedAttrs,
                    owner = owner,
                    model = newModel,
                    notation = srcDiagram.notation,
                    node = newNode,
                    deleted = false
                )
            )
        }

        return modelsRepository.findById(newModel.id!!).orElseThrow { IllegalStateException("New model not found") }
    }

    private fun remapDiagramAttrs(
        attrs: String?,
        nodeIdMap: Map<UUID, UUID>,
        linkIdMap: Map<UUID, UUID>
    ): String? {
        if (attrs.isNullOrBlank()) return attrs
        return try {
            val tree = objectMapper.readTree(attrs) ?: return attrs
            val instances = tree.get("instances") ?: return objectMapper.writeValueAsString(tree)
            val nodes = instances.get("nodes") ?: return objectMapper.writeValueAsString(tree)
            for (i in 0 until nodes.size()) {
                val node = nodes.get(i) ?: continue
                val modelNodeId = node.get("modelNodeId")?.asText() ?: continue
                val oldUuid = try {
                    UUID.fromString(modelNodeId)
                } catch (ex: IllegalArgumentException) {
                    log.warn("Ignoring invalid modelNodeId while copying model: {}", modelNodeId, ex)
                    continue
                }
                val newUuid = nodeIdMap[oldUuid] ?: continue
                (node as? ObjectNode)?.put("modelNodeId", newUuid.toString())
            }
            val edges = instances.get("edges")
            if (edges != null && edges.isArray) {
                for (i in 0 until edges.size()) {
                    val edge = edges.get(i) ?: continue
                    val modelLinkId = edge.get("modelLinkId")?.asText() ?: continue
                    val oldUuid = try {
                        UUID.fromString(modelLinkId)
                    } catch (ex: IllegalArgumentException) {
                        log.warn("Ignoring invalid modelLinkId while copying model: {}", modelLinkId, ex)
                        continue
                    }
                    val newUuid = linkIdMap[oldUuid] ?: continue
                    (edge as? ObjectNode)?.put("modelLinkId", newUuid.toString())
                }
            }
            objectMapper.writeValueAsString(tree)
        } catch (ex: JsonProcessingException) {
            log.warn("Could not remap diagram attrs while copying model; preserving original attrs", ex)
            attrs
        }
    }

}
