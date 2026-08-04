package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramConflictException
import ru.kavader.arepos.dto.model.DiagramInstanceNodeInput
import ru.kavader.arepos.dto.model.DiagramInstancesMergeCounts
import ru.kavader.arepos.dto.model.DiagramInstancesMergeRequest
import ru.kavader.arepos.dto.model.DiagramInstancesMergeResponse
import ru.kavader.arepos.dto.system.ModelSyncChangeType
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.dto.system.ModelSyncEventType
import ru.kavader.arepos.mapper.ModelMapper
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class DiagramInstancesMergeService(
    private val diagramsRepository: DiagramsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val accessService: ResourceAccessService,
    private val diagramLifecycleService: DiagramLifecycleService,
    private val mdFileLinkValidator: MdFileLinkValidator,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val modelMapper: ModelMapper,
    private val notationBindingService: NotationBindingService,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun merge(diagramId: UUID, request: DiagramInstancesMergeRequest): DiagramInstancesMergeResponse {
        val diagram = diagramsRepository.findById(diagramId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $diagramId not found")
        }
        accessService.requireCanEditDiagram(diagram)
        diagramLifecycleService.requireLatestDiagramVersion(diagram, "updated")

        if (request.baseUpdatedAt != null) {
            val serverUpdatedAt = diagram.updatedAt ?: diagram.createdAt
            if (serverUpdatedAt != null && !serverUpdatedAt.equals(request.baseUpdatedAt)) {
                throw DiagramConflictException(
                    message = "Diagram attrs were modified concurrently",
                    diagramId = diagramId,
                    serverUpdatedAt = serverUpdatedAt,
                    clientBaseUpdatedAt = request.baseUpdatedAt
                )
            }
        }

        val root = parseAttrsRoot(diagram.attrs)
        val instances = (root.get("instances") as? ObjectNode)
            ?: objectMapper.createObjectNode().also { root.set<ObjectNode>("instances", it) }
        val nodesArr = (instances.get("nodes") as? ArrayNode)
            ?: objectMapper.createArrayNode().also { instances.set<ArrayNode>("nodes", it) }
        val edgesArr = (instances.get("edges") as? ArrayNode)
            ?: objectMapper.createArrayNode().also { instances.set<ArrayNode>("edges", it) }

        var nodesAdded = 0
        var nodesUpdated = 0
        var edgesAdded = 0
        var edgesUpdated = 0

        val notationId = diagram.notation.id!!

        for (input in request.nodes) {
            val modelNode = nodesRepository.findById(input.modelNodeId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node ${input.modelNodeId} not found")
            }
            if (modelNode.model.id != diagram.model.id) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Node ${input.modelNodeId} does not belong to diagram model ${diagram.model.id}"
                )
            }
            val existingIdx = indexOfNodeByModelNodeId(nodesArr, input.modelNodeId)
            if (existingIdx >= 0) {
                val node = nodesArr.get(existingIdx) as ObjectNode
                applyNodeGeometry(node, input)
                nodesUpdated++
            } else {
                val node = objectMapper.createObjectNode()
                node.put("id", input.id?.takeIf { it.isNotBlank() } ?: "n_${UUID.randomUUID()}")
                node.put("modelNodeId", input.modelNodeId.toString())
                applyNodeGeometry(node, input)
                val componentId = notationBindingService.readNodeComponentId(modelNode.attrs, notationId)
                if (componentId != null) {
                    val attrs = objectMapper.createObjectNode()
                    attrs.put("notationComponentId", componentId.toString())
                    node.set<ObjectNode>("attrs", attrs)
                }
                nodesArr.add(node)
                nodesAdded++
            }
        }

        val instanceIdByModelNodeId = mutableMapOf<UUID, String>()
        for (el in nodesArr) {
            if (el !is ObjectNode) continue
            val mid = el.get("modelNodeId")?.asText() ?: continue
            val iid = el.get("id")?.asText() ?: continue
            runCatching { UUID.fromString(mid) }.getOrNull()?.let { instanceIdByModelNodeId[it] = iid }
        }

        for (input in request.edges) {
            val link = linksRepository.findById(input.modelLinkId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link ${input.modelLinkId} not found")
            }
            if (link.model.id != diagram.model.id) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Link ${input.modelLinkId} does not belong to diagram model ${diagram.model.id}"
                )
            }
            val sourceInstanceId = resolveEndpointInstanceId(
                explicit = input.sourceInstanceId,
                modelNodeId = link.source.id!!,
                instanceIdByModelNodeId = instanceIdByModelNodeId,
                role = "source",
                modelLinkId = input.modelLinkId
            )
            val targetInstanceId = resolveEndpointInstanceId(
                explicit = input.targetInstanceId,
                modelNodeId = link.target.id!!,
                instanceIdByModelNodeId = instanceIdByModelNodeId,
                role = "target",
                modelLinkId = input.modelLinkId
            )

            val existingIdx = indexOfEdgeByModelLinkId(edgesArr, input.modelLinkId)
            if (existingIdx >= 0) {
                val edge = edgesArr.get(existingIdx) as ObjectNode
                edge.put("sourceInstanceId", sourceInstanceId)
                edge.put("targetInstanceId", targetInstanceId)
                edgesUpdated++
            } else {
                val edge = objectMapper.createObjectNode()
                edge.put("id", input.id?.takeIf { it.isNotBlank() } ?: "e_${UUID.randomUUID()}")
                edge.put("modelLinkId", input.modelLinkId.toString())
                edge.put("sourceInstanceId", sourceInstanceId)
                edge.put("targetInstanceId", targetInstanceId)
                edgesArr.add(edge)
                edgesAdded++
            }
        }

        val newAttrs = objectMapper.writeValueAsString(root)
        mdFileLinkValidator.validate(newAttrs)
        diagram.attrs = newAttrs
        diagram.updatedAt = Instant.now()
        val updated = diagramsRepository.save(diagram)
        modelSyncBroadcaster.broadcastModelChanged(
            requireNotNull(diagram.model.id),
            ModelSyncChangeType.DIAGRAM_UPDATE.wireValue,
            listOf(
                ModelSyncEntityEvent(
                    ModelSyncEventType.DIAGRAM_UPDATED.wireValue,
                    ModelSyncEventType.DIAGRAM_UPDATED.entity,
                    requireNotNull(updated.id)
                )
            )
        )
        return DiagramInstancesMergeResponse(
            diagram = modelMapper.toResponse(updated),
            counts = DiagramInstancesMergeCounts(
                nodesAdded = nodesAdded,
                nodesUpdated = nodesUpdated,
                edgesAdded = edgesAdded,
                edgesUpdated = edgesUpdated
            )
        )
    }

    private fun parseAttrsRoot(attrs: String?): ObjectNode {
        if (attrs.isNullOrBlank()) {
            return objectMapper.createObjectNode()
        }
        return try {
            val tree = objectMapper.readTree(attrs)
            if (tree is ObjectNode) tree.deepCopy() else objectMapper.createObjectNode()
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "diagram attrs must be a JSON object")
        }
    }

    private fun applyNodeGeometry(node: ObjectNode, input: DiagramInstanceNodeInput) {
        node.put("x", input.x)
        node.put("y", input.y)
        if (input.width != null) node.put("width", input.width)
        if (input.height != null) node.put("height", input.height)
    }

    private fun indexOfNodeByModelNodeId(nodes: ArrayNode, modelNodeId: UUID): Int {
        val key = modelNodeId.toString()
        for (i in 0 until nodes.size()) {
            val el = nodes.get(i)
            if (el is ObjectNode && el.get("modelNodeId")?.asText() == key) return i
        }
        return -1
    }

    private fun indexOfEdgeByModelLinkId(edges: ArrayNode, modelLinkId: UUID): Int {
        val key = modelLinkId.toString()
        for (i in 0 until edges.size()) {
            val el = edges.get(i)
            if (el is ObjectNode && el.get("modelLinkId")?.asText() == key) return i
        }
        return -1
    }

    private fun resolveEndpointInstanceId(
        explicit: String?,
        modelNodeId: UUID,
        instanceIdByModelNodeId: Map<UUID, String>,
        role: String,
        modelLinkId: UUID
    ): String {
        if (!explicit.isNullOrBlank()) return explicit
        return instanceIdByModelNodeId[modelNodeId]
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot resolve $role instance for link $modelLinkId: no diagram instance for node $modelNodeId"
            )
    }
}
