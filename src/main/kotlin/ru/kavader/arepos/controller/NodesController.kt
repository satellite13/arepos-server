package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nodes")
class NodesController(
    private val nodesRepository: NodesRepository,
    private val modelsRepository: ModelsRepository,
    private val nodeTypesRepository: NodeTypesRepository,
    private val usersRepository: UsersRepository
) {

    @GetMapping
    fun listNodes(
        pageable: Pageable,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeResponse> {
        val nodes = when {
            modelId != null -> {
                val model = modelsRepository.findById(modelId).orElse(null)
                if (model != null) {
                    nodesRepository.findByModel(model, pageable)
                } else {
                    nodesRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    nodesRepository.findByOwner(owner, pageable)
                } else {
                    nodesRepository.findAll(pageable)
                }
            }
            name != null -> {
                nodesRepository.findByNameContainingIgnoreCase(name, pageable)
            }
            else -> {
                nodesRepository.findAll(pageable)
            }
        }
        return nodes.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getNode(@PathVariable id: UUID): NodeResponse =
        nodesRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNode(@RequestBody request: NodeRequest): NodeResponse {
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val nodeType = nodeTypesRepository.findById(request.nodeTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType ${request.nodeTypeId} not found")
            }
        val parentNode = request.parentNodeId?.let {
            nodesRepository.findById(it).orElse(null)
        }

        val saved = nodesRepository.save(
            Nodes(
                name = request.name,
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = parentNode,
                attrs = request.attrs,
                createdAt = Instant.now()
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateNode(
        @PathVariable id: UUID,
        @RequestBody request: NodeUpdateRequest
    ): NodeResponse {
        val node = nodesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")
            }
        
        val model = request.modelId?.let {
            modelsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $it not found")
            }
        } ?: node.model
        
        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: node.owner
        
        val nodeType = request.nodeTypeId?.let {
            nodeTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $it not found")
            }
        } ?: node.nodeType
        
        val parentNode = request.parentNodeId?.let {
            if (it == id) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Node cannot be its own parent")
            }
            nodesRepository.findById(it).orElse(null)
        } ?: node.parentNode

        val updated = nodesRepository.save(
            node.copy(
                name = request.name ?: node.name,
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = parentNode,
                attrs = request.attrs ?: node.attrs
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNode(@PathVariable id: UUID) {
        if (!nodesRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Node $id not found")
        }
        nodesRepository.deleteById(id)
    }

    private fun Nodes.toResponse() = NodeResponse(
        id = requireNotNull(id),
        name = name,
        modelId = model.id!!,
        ownerId = owner.id!!,
        nodeTypeId = nodeType.id!!,
        parentNodeId = parentNode?.id,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class NodeRequest(
    val name: String,
    val modelId: UUID,
    val ownerId: UUID,
    val nodeTypeId: UUID,
    val parentNodeId: UUID? = null,
    val attrs: String? = null
)

data class NodeUpdateRequest(
    val name: String? = null,
    val modelId: UUID? = null,
    val ownerId: UUID? = null,
    val nodeTypeId: UUID? = null,
    val parentNodeId: UUID? = null,
    val attrs: String? = null
)

data class NodeResponse(
    val id: UUID,
    val name: String,
    val modelId: UUID,
    val ownerId: UUID,
    val nodeTypeId: UUID,
    val parentNodeId: UUID?,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

