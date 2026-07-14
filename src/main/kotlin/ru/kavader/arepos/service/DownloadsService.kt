package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.site.DownloadAssetResponse
import ru.kavader.arepos.model.DownloadAsset
import ru.kavader.arepos.repository.DownloadAssetRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
@ConditionalOnBean(FileStorageService::class)
class DownloadsService(
    private val downloadAssetRepository: DownloadAssetRepository,
    private val fileStorageService: FileStorageService,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val objectMapper: ObjectMapper
) {
    fun listPublished(): List<DownloadAssetResponse> =
        downloadAssetRepository.findByPublishedTrueOrderBySortOrderAsc().map(::toResponse)

    fun listAll(): List<DownloadAssetResponse> {
        accessService.requireCanManageDownloads()
        return downloadAssetRepository.findAllByOrderBySortOrderAsc().map(::toResponse)
    }

    @Transactional
    fun create(
        title: String,
        description: String,
        kind: String,
        versionLabel: String?,
        sortOrder: Int,
        published: Boolean,
        file: MultipartFile
    ): DownloadAssetResponse {
        accessService.requireCanManageDownloads()
        val normalizedKind = normalizeKind(kind)
        validateNotationExport(normalizedKind, file)
        val owner = usersRepository.findById(accessService.currentUserId())
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        val stored = fileStorageService.uploadSiteAsset(file, owner)
        val now = Instant.now()
        val saved = downloadAssetRepository.save(
            DownloadAsset(
                title = validateTitle(title),
                description = description.trim(),
                kind = normalizedKind,
                file = stored,
                fileName = stored.filename,
                contentType = stored.contentType,
                sizeBytes = stored.size,
                versionLabel = versionLabel?.trim()?.takeIf { it.isNotEmpty() },
                sortOrder = sortOrder,
                published = published,
                downloadCount = 0,
                createdAt = now,
                updatedAt = now
            )
        )
        return toResponse(saved)
    }

    @Transactional
    fun setPublished(id: UUID, published: Boolean): DownloadAssetResponse {
        accessService.requireCanManageDownloads()
        val asset = findAsset(id)
        asset.published = published
        asset.updatedAt = Instant.now()
        return toResponse(downloadAssetRepository.save(asset))
    }

    @Transactional
    fun delete(id: UUID) {
        accessService.requireCanManageDownloads()
        downloadAssetRepository.delete(findAsset(id))
    }

    @Transactional
    fun download(id: UUID): Pair<DownloadAsset, Resource> {
        accessService.requireCanDownloadAsset()
        val asset = findAsset(id)
        if (!asset.published) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Download not found")
        }
        val pair = fileStorageService.getFile(asset.file.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        asset.downloadCount += 1
        asset.updatedAt = Instant.now()
        downloadAssetRepository.save(asset)
        return asset to pair.second
    }

    private fun findAsset(id: UUID): DownloadAsset =
        downloadAssetRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Download not found") }

    private fun toResponse(asset: DownloadAsset): DownloadAssetResponse =
        DownloadAssetResponse(
            id = asset.id!!,
            title = asset.title,
            description = asset.description,
            kind = asset.kind,
            fileName = asset.fileName,
            contentType = asset.contentType,
            sizeBytes = asset.sizeBytes,
            versionLabel = asset.versionLabel,
            sortOrder = asset.sortOrder,
            published = asset.published,
            downloadCount = asset.downloadCount,
            createdAt = asset.createdAt,
            updatedAt = asset.updatedAt
        )

    private fun validateTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid title")
        }
        return trimmed
    }

    private fun normalizeKind(kind: String): String {
        val normalized = kind.trim().lowercase()
        if (normalized !in ALLOWED_KINDS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid kind")
        }
        return normalized
    }

    private fun validateNotationExport(kind: String, file: MultipartFile) {
        if (kind != "notation_export") return
        try {
            val tree = objectMapper.readTree(file.bytes)
            val format = tree.get("format")?.asText()
            if (format != "warchi-notation-export") {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "notation_export must have format=warchi-notation-export"
                )
            }
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notation export JSON")
        }
    }

    companion object {
        private val ALLOWED_KINDS = setOf("notation_export", "other")
    }
}
