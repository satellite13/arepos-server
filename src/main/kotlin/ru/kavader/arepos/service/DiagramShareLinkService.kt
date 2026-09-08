package ru.kavader.arepos.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramShareLinkRequest
import ru.kavader.arepos.dto.model.DiagramShareLinkResponse
import ru.kavader.arepos.model.DiagramPreviewLinks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramPreviewLinksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.DIAGRAM_SHARE_LINK_REVOKE_DENIED
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class DiagramShareLinkService(
    private val diagramsRepository: DiagramsRepository,
    private val usersRepository: UsersRepository,
    private val modelsRepository: ModelsRepository,
    private val diagramPreviewLinksRepository: DiagramPreviewLinksRepository,
    private val diagramSvgStorage: DiagramSvgStorage,
    private val accessService: ResourceAccessService,
    private val diagramLifecycleService: DiagramLifecycleService,
    @Value($$"${arepos.public-url-base:}") private val publicUrlBase: String = ""
) {
    companion object {
        private const val PUBLIC_SVG_URL_PREFIX = "/api/v1/diagrams/svg/public/"
    }

    @Transactional
    fun createShareLink(request: DiagramShareLinkRequest): DiagramShareLinkResponse {
        val currentUser = accessService.currentUserId()
        val user = usersRepository.findById(currentUser)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")
            }
        return when {
            request.diagramId != null && request.latest == true -> {
                val seed = diagramsRepository.findById(request.diagramId)
                    .orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram ${request.diagramId} not found")
                    }
                accessService.requireCanViewDiagram(seed)
                createOrReuseLatestLink(seed, user)
            }

            request.diagramId != null -> {
                val diagram = diagramsRepository.findById(request.diagramId)
                    .orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram ${request.diagramId} not found")
                    }
                accessService.requireCanViewDiagram(diagram)
                val existing = diagramPreviewLinksRepository.findByDiagramAndLatest(diagram, false)
                val link = if (existing.isPresent) {
                    existing.get()
                } else {
                    diagramPreviewLinksRepository.save(
                        DiagramPreviewLinks(
                            token = UUID.randomUUID(),
                            diagram = diagram,
                            model = null,
                            diagramName = null,
                            latest = false,
                            createdAt = Instant.now(),
                            createdBy = user
                        )
                    )
                }
                toResponse(link, diagram.id!!)
            }

            request.modelId != null && request.diagramName != null && request.latest == true -> {
                val model = modelsRepository.findById(request.modelId)
                    .orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
                    }
                accessService.requireCanViewModel(model)
                val latest = resolveLatestDiagramByName(model, request.diagramName, enforceViewAccess = true)
                createOrReuseLatestLink(latest, user)
            }

            else -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide diagramId, (diagramId, latest: true), or (modelId, diagramName, latest: true)"
            )
        }
    }

    @Transactional(readOnly = true)
    fun resolvePublicSvg(token: UUID): ByteArray {
        val link = diagramPreviewLinksRepository.findByTokenWithTargets(token).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found or expired")
        val diagramId = resolveTargetDiagramId(link)
        return when (val svgResult = diagramSvgStorage.getSvg(diagramId)) {
            is DiagramSvgReadResult.Found -> svgResult.bytes
            DiagramSvgReadResult.NotFound ->
                throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Preview not found. The diagram owner can upload it in the editor."
                )

            is DiagramSvgReadResult.StorageError ->
                throw ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Preview storage is temporarily unavailable"
                )
        }
    }

    @Transactional
    fun revokeShareLink(token: UUID) {
        val link = diagramPreviewLinksRepository.findByTokenWithTargets(token)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found")
            }
        val canRevoke = when {
            link.diagram != null -> accessService.canEditDiagram(link.diagram!!)
            link.model != null -> accessService.canEditModel(link.model!!)
            else -> false
        }
        if (!canRevoke) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, DIAGRAM_SHARE_LINK_REVOKE_DENIED)
        }
        diagramPreviewLinksRepository.delete(link)
    }

    fun buildPublicSvgUrl(token: UUID): String {
        val normalizedBase = publicUrlBase.trim().removeSuffix("/")
        val path = "$PUBLIC_SVG_URL_PREFIX$token"
        return if (normalizedBase.isBlank()) path else "$normalizedBase$path"
    }

    private fun createOrReuseLatestLink(seed: Diagrams, user: Users): DiagramShareLinkResponse {
        val seriesId = requireSeriesId(seed)
        val head = resolveLatestDiagramBySeries(seriesId, enforceViewAccess = true)
        val existing = findExistingLatestLink(seed, head, seriesId)
        val link = if (existing != null) {
            existing.diagram = head
            existing.model = null
            existing.diagramName = null
            existing.latest = true
            diagramPreviewLinksRepository.save(existing)
        } else {
            diagramPreviewLinksRepository.save(
                DiagramPreviewLinks(
                    token = UUID.randomUUID(),
                    diagram = head,
                    model = null,
                    diagramName = null,
                    latest = true,
                    createdAt = Instant.now(),
                    createdBy = user
                )
            )
        }
        return toResponse(link, head.id!!)
    }

    private fun findExistingLatestLink(seed: Diagrams, head: Diagrams, seriesId: UUID): DiagramPreviewLinks? {
        val bySeries = diagramPreviewLinksRepository.findByLatestTrueAndDiagramSeriesId(seriesId)
        if (bySeries.isPresent) return bySeries.get()
        val byHead = diagramPreviewLinksRepository.findByDiagramAndLatest(head, true)
        if (byHead.isPresent) return byHead.get()
        val bySeed = diagramPreviewLinksRepository.findByDiagramAndLatest(seed, true)
        if (bySeed.isPresent) return bySeed.get()
        val byHeadName = diagramPreviewLinksRepository.findByModelAndDiagramName(head.model, head.name)
        if (byHeadName.isPresent) return byHeadName.get()
        return diagramPreviewLinksRepository.findByModelAndDiagramName(seed.model, seed.name).orElse(null)
    }

    private fun resolveLatestDiagramByName(
        model: Models,
        diagramName: String,
        enforceViewAccess: Boolean
    ): Diagrams {
        val candidates = diagramsRepository.findByModelIdAndNameAndDeletedFalse(model.id!!, diagramName)
        val allByName = if (enforceViewAccess) {
            accessService.filterViewableDiagrams(candidates)
        } else {
            candidates
        }
        return allByName.maxWithOrNull(diagramLifecycleService::compareDiagramVersions)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No diagram named '$diagramName' found"
            )
    }

    private fun resolveLatestDiagramBySeries(seriesId: UUID, enforceViewAccess: Boolean): Diagrams {
        val candidates = diagramsRepository.findBySeriesIdAndDeletedFalse(seriesId)
        val visible = if (enforceViewAccess) {
            accessService.filterViewableDiagrams(candidates)
        } else {
            candidates
        }
        return visible.maxWithOrNull(diagramLifecycleService::compareDiagramVersions)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No diagram found for share series"
            )
    }

    private fun resolveTargetDiagramId(link: DiagramPreviewLinks): UUID {
        val linkedDiagram = link.diagram
        if (linkedDiagram != null && link.latest) {
            val seriesId = requireSeriesId(linkedDiagram)
            return resolveLatestDiagramBySeries(seriesId, enforceViewAccess = false).id!!
        }
        if (linkedDiagram != null) {
            return linkedDiagram.id!!
        }
        val linkedModel = link.model
        val linkedDiagramName = link.diagramName
        if (linkedModel != null && linkedDiagramName != null) {
            return resolveLatestDiagramByName(linkedModel, linkedDiagramName, enforceViewAccess = false).id!!
        }
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid share link")
    }

    private fun requireSeriesId(diagram: Diagrams): UUID =
        diagram.seriesId ?: diagram.id
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid share link")

    private fun toResponse(link: DiagramPreviewLinks, diagramId: UUID) = DiagramShareLinkResponse(
        url = buildPublicSvgUrl(link.token),
        token = link.token,
        diagramId = diagramId
    )
}
