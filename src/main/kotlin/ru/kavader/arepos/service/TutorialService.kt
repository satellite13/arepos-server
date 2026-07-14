package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.site.CreateTutorialRequest
import ru.kavader.arepos.dto.site.TutorialVideoResponse
import ru.kavader.arepos.dto.site.UpdateTutorialRequest
import ru.kavader.arepos.model.TutorialVideo
import ru.kavader.arepos.repository.TutorialVideoRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.net.URI
import java.time.Instant
import java.util.UUID

@Service
class TutorialService(
    private val tutorialVideoRepository: TutorialVideoRepository,
    private val accessService: ResourceAccessService
) {
    fun listPublished(): List<TutorialVideoResponse> =
        tutorialVideoRepository.findByPublishedTrueOrderBySortOrderAsc().map(::toResponse)

    fun listAll(): List<TutorialVideoResponse> {
        accessService.requireCanManageTutorials()
        return tutorialVideoRepository.findAllByOrderBySortOrderAsc().map(::toResponse)
    }

    @Transactional
    fun create(request: CreateTutorialRequest): TutorialVideoResponse {
        accessService.requireCanManageTutorials()
        val provider = normalizeProvider(request.provider)
        val externalId = request.externalId.trim()
        if (externalId.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "externalId required")
        }
        val embedUrl = (request.embedUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: buildEmbedUrl(provider, externalId))
        validateEmbedUrl(embedUrl)
        val now = Instant.now()
        val saved = tutorialVideoRepository.save(
            TutorialVideo(
                title = validateTitle(request.title),
                description = request.description.trim(),
                provider = provider,
                externalId = externalId,
                embedUrl = embedUrl,
                thumbnailUrl = request.thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() },
                sortOrder = request.sortOrder,
                published = request.published,
                createdAt = now,
                updatedAt = now
            )
        )
        return toResponse(saved)
    }

    @Transactional
    fun update(id: UUID, request: UpdateTutorialRequest): TutorialVideoResponse {
        accessService.requireCanManageTutorials()
        val video = tutorialVideoRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Tutorial not found") }
        request.title?.let { video.title = validateTitle(it) }
        request.description?.let { video.description = it.trim() }
        request.provider?.let { video.provider = normalizeProvider(it) }
        request.externalId?.let { video.externalId = it.trim() }
        request.embedUrl?.let {
            validateEmbedUrl(it.trim())
            video.embedUrl = it.trim()
        }
        if (request.thumbnailUrl != null) {
            video.thumbnailUrl = request.thumbnailUrl.trim().takeIf { it.isNotEmpty() }
        }
        request.sortOrder?.let { video.sortOrder = it }
        request.published?.let { video.published = it }
        video.updatedAt = Instant.now()
        return toResponse(tutorialVideoRepository.save(video))
    }

    @Transactional
    fun delete(id: UUID) {
        accessService.requireCanManageTutorials()
        val video = tutorialVideoRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Tutorial not found") }
        tutorialVideoRepository.delete(video)
    }

    private fun toResponse(video: TutorialVideo): TutorialVideoResponse =
        TutorialVideoResponse(
            id = video.id!!,
            title = video.title,
            description = video.description,
            provider = video.provider,
            externalId = video.externalId,
            embedUrl = video.embedUrl,
            thumbnailUrl = video.thumbnailUrl,
            sortOrder = video.sortOrder,
            published = video.published,
            createdAt = video.createdAt,
            updatedAt = video.updatedAt
        )

    private fun validateTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid title")
        }
        return trimmed
    }

    private fun normalizeProvider(provider: String): String {
        val normalized = provider.trim().lowercase()
        if (normalized !in ALLOWED_PROVIDERS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid provider")
        }
        return normalized
    }

    private fun buildEmbedUrl(provider: String, externalId: String): String =
        when (provider) {
            "youtube" -> "https://www.youtube.com/embed/$externalId"
            "rutube" -> "https://rutube.ru/play/embed/$externalId"
            "vk" -> "https://vk.com/video_ext.php?oid=${externalId.substringBefore('_')}&id=${externalId.substringAfter('_')}"
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid provider")
        }

    private fun validateEmbedUrl(url: String) {
        val host = try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid embed URL")
        if (ALLOWED_EMBED_HOSTS.none { host == it || host.endsWith(".$it") }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Embed host not allowed: $host")
        }
    }

    companion object {
        private val ALLOWED_PROVIDERS = setOf("youtube", "rutube", "vk")
        private val ALLOWED_EMBED_HOSTS = setOf(
            "youtube.com",
            "www.youtube.com",
            "youtu.be",
            "rutube.ru",
            "www.rutube.ru",
            "vk.com",
            "www.vk.com",
            "vkvideo.ru",
            "www.vkvideo.ru"
        )
    }
}
