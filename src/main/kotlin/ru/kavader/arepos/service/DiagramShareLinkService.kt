package ru.kavader.arepos.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramShareLinkRequest
import ru.kavader.arepos.dto.model.DiagramShareLinkResponse
import ru.kavader.arepos.model.DiagramPreviewLinks
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
        val link: DiagramPreviewLinks = when {
            request.diagramId != null -> {
                val diagram = diagramsRepository.findById(request.diagramId)
                    .orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram ${request.diagramId} not found")
                    }
                accessService.requireCanViewDiagram(diagram)
                val existing = diagramPreviewLinksRepository.findByDiagram(diagram)
                if (existing.isPresent) {
                    return toResponse(existing.get())
                }
                diagramPreviewLinksRepository.save(
                    DiagramPreviewLinks(
                        token = UUID.randomUUID(),
                        diagram = diagram,
                        model = null,
                        diagramName = null,
                        createdAt = Instant.now(),
                        createdBy = user
                    )
                )
            }

            request.modelId != null && request.diagramName != null && request.latest == true -> {
                val model = modelsRepository.findById(request.modelId)
                    .orElseThrow {
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Model ${request.modelId} not found")
                    }
                accessService.requireCanViewModel(model)
                val allByName = diagramsRepository.findByModelIdAndNameAndDeletedFalse(model.id!!, request.diagramName)
                    .let { accessService.filterViewableDiagrams(it) }
                allByName.maxWithOrNull(diagramLifecycleService::compareDiagramVersions)
                    ?: throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No diagram named '${request.diagramName}' found"
                    )
                val existing = diagramPreviewLinksRepository.findByModelAndDiagramName(model, request.diagramName)
                if (existing.isPresent) {
                    return toResponse(existing.get())
                }
                diagramPreviewLinksRepository.save(
                    DiagramPreviewLinks(
                        token = UUID.randomUUID(),
                        diagram = null,
                        model = model,
                        diagramName = request.diagramName,
                        createdAt = Instant.now(),
                        createdBy = user
                    )
                )
            }

            else -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide either diagramId or (modelId, diagramName, latest: true)"
            )
        }
        return toResponse(link)
    }

    fun resolvePublicSvg(token: UUID): ByteArray {
        val link = diagramPreviewLinksRepository.findByToken(token).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found or expired")
        val linkedDiagram = link.diagram
        val linkedModel = link.model
        val linkedDiagramName = link.diagramName
        val diagramId: UUID = when {
            linkedDiagram != null -> linkedDiagram.id!!
            linkedModel != null && linkedDiagramName != null -> {
                val allByName = diagramsRepository.findByModelIdAndNameAndDeletedFalse(
                    linkedModel.id!!,
                    linkedDiagramName
                )
                val latest = allByName.maxWithOrNull(diagramLifecycleService::compareDiagramVersions)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram not found")
                latest.id!!
            }

            else -> throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid share link")
        }
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
        val link = diagramPreviewLinksRepository.findByToken(token)
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

    private fun toResponse(link: DiagramPreviewLinks) = DiagramShareLinkResponse(
        url = buildPublicSvgUrl(link.token),
        token = link.token
    )
}
