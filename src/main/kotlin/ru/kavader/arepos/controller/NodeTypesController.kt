package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/node-types")
class NodeTypesController(
    private val nodeTypesRepository: NodeTypesRepository,
    private val usersRepository: UsersRepository
) {

    @GetMapping
    fun listNodeTypes(
        pageable: Pageable,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) name: String?
    ): Page<NodeTypeResponse> {
        val nodeTypes = when {
            ownerId != null && name != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    nodeTypesRepository.findByOwnerAndNameContainingIgnoreCase(owner, name, pageable)
                } else {
                    nodeTypesRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    nodeTypesRepository.findByOwner(owner, pageable)
                } else {
                    nodeTypesRepository.findAll(pageable)
                }
            }
            name != null -> {
                nodeTypesRepository.findByNameContainingIgnoreCase(name, pageable)
            }
            else -> {
                nodeTypesRepository.findAll(pageable)
            }
        }
        return nodeTypes.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getNodeType(@PathVariable id: UUID): NodeTypeResponse =
        nodeTypesRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNodeType(@RequestBody request: NodeTypeRequest): NodeTypeResponse {
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val now = Instant.now()
        val saved = nodeTypesRepository.save(
            NodeTypes(
                name = request.name,
                createdAt = now,
                updatedAt = now,
                attrs = request.attrs,
                owner = owner
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateNodeType(
        @PathVariable id: UUID,
        @RequestBody request: NodeTypeUpdateRequest
    ): NodeTypeResponse {
        val nodeType = nodeTypesRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
            }
        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: nodeType.owner

        val updated = nodeTypesRepository.save(
            nodeType.copy(
                name = request.name ?: nodeType.name,
                attrs = request.attrs ?: nodeType.attrs,
                owner = owner
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNodeType(@PathVariable id: UUID) {
        if (!nodeTypesRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "NodeType $id not found")
        }
        nodeTypesRepository.deleteById(id)
    }

    private fun NodeTypes.toResponse() = NodeTypeResponse(
        id = requireNotNull(id),
        name = name,
        ownerId = owner.id!!,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class NodeTypeRequest(
    val name: String,
    val ownerId: UUID,
    val attrs: String? = null
)

data class NodeTypeUpdateRequest(
    val name: String? = null,
    val ownerId: UUID? = null,
    val attrs: String? = null
)

data class NodeTypeResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val attrs: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

