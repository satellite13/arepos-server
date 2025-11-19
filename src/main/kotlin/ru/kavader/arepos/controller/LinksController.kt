package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/links")
class LinksController(
    private val linksRepository: LinksRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val nodesRepository: NodesRepository,
    private val linkTypesRepository: LinkTypesRepository
) {

    @GetMapping
    fun listLinks(
        pageable: Pageable,
        @RequestParam(required = false) modelId: UUID?,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) sourceId: UUID?,
        @RequestParam(required = false) targetId: UUID?,
        @RequestParam(required = false) linkTypeId: UUID?
    ): Page<LinkResponse> {
        val links = when {
            modelId != null && ownerId != null -> {
                val model = modelsRepository.findById(modelId).orElse(null)
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (model != null && owner != null) {
                    linksRepository.findByModelAndOwner(model, owner, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            modelId != null -> {
                val model = modelsRepository.findById(modelId).orElse(null)
                if (model != null) {
                    linksRepository.findByModel(model, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            ownerId != null -> {
                val owner = usersRepository.findById(ownerId).orElse(null)
                if (owner != null) {
                    linksRepository.findByOwner(owner, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            sourceId != null -> {
                val source = nodesRepository.findById(sourceId).orElse(null)
                if (source != null) {
                    linksRepository.findBySource(source, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            targetId != null -> {
                val target = nodesRepository.findById(targetId).orElse(null)
                if (target != null) {
                    linksRepository.findByTarget(target, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            linkTypeId != null -> {
                val linkType = linkTypesRepository.findById(linkTypeId).orElse(null)
                if (linkType != null) {
                    linksRepository.findByLinkType(linkType, pageable)
                } else {
                    linksRepository.findAll(pageable)
                }
            }
            else -> {
                linksRepository.findAll(pageable)
            }
        }
        return links.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getLink(@PathVariable id: UUID): LinkResponse =
        linksRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createLink(@RequestBody request: LinkRequest): LinkResponse {
        val owner = usersRepository.findById(request.ownerId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner ${request.ownerId} not found")
            }
        val model = modelsRepository.findById(request.modelId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
            }
        val source = nodesRepository.findById(request.sourceId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Source node ${request.sourceId} not found")
            }
        val target = nodesRepository.findById(request.targetId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Target node ${request.targetId} not found")
            }
        val linkType = linkTypesRepository.findById(request.linkTypeId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType ${request.linkTypeId} not found")
            }
        val saved = linksRepository.save(
            Links(
                source = source,
                target = target,
                createdAt = Instant.now(),
                attrs = request.attrs,
                owner = owner,
                linkType = linkType,
                model = model
            )
        )
        return saved.toResponse()
    }

    @PutMapping("/{id}")
    fun updateLink(
        @PathVariable id: UUID,
        @RequestBody request: LinkUpdateRequest
    ): LinkResponse {
        val link = linksRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
            }
        val owner = request.ownerId?.let {
            usersRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
            }
        } ?: link.owner
        val model = request.modelId?.let {
            modelsRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Model $it not found")
            }
        } ?: link.model
        val source = request.sourceId?.let {
            nodesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Source node $it not found")
            }
        } ?: link.source
        val target = request.targetId?.let {
            nodesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Target node $it not found")
            }
        } ?: link.target
        val linkType = request.linkTypeId?.let {
            linkTypesRepository.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "LinkType $it not found")
            }
        } ?: link.linkType

        val updated = linksRepository.save(
            link.copy(
                source = source,
                target = target,
                attrs = request.attrs ?: link.attrs,
                owner = owner,
                linkType = linkType,
                model = model
            )
        )
        return updated.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLink(@PathVariable id: UUID) {
        if (!linksRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Link $id not found")
        }
        linksRepository.deleteById(id)
    }

    private fun Links.toResponse() = LinkResponse(
        id = requireNotNull(id),
        sourceId = source.id!!,
        targetId = target.id!!,
        modelId = model.id!!,
        ownerId = owner.id!!,
        linkTypeId = linkType.id!!,
        attrs = attrs
    )
}

data class LinkRequest(
    val sourceId: UUID,
    val targetId: UUID,
    val modelId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String? = null
)

data class LinkUpdateRequest(
    val sourceId: UUID? = null,
    val targetId: UUID? = null,
    val modelId: UUID? = null,
    val ownerId: UUID? = null,
    val linkTypeId: UUID? = null,
    val attrs: String? = null
)

data class LinkResponse(
    val id: UUID,
    val sourceId: UUID,
    val targetId: UUID,
    val modelId: UUID,
    val ownerId: UUID,
    val linkTypeId: UUID,
    val attrs: String?
)

