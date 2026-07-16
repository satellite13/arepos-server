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
                fileName = readableFileName(stored.filename, title),
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

    /**
     * Prefer a title-based ASCII name when multipart filename was already mangled
     * (e.g. leading underscore after non-ASCII characters were replaced).
     */
    internal fun readableFileName(storedFileName: String, title: String): String {
        val trimmed = storedFileName.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("_") && !trimmed.startsWith("-")) {
            return trimmed
        }
        val ext = trimmed.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() && it.all { ch -> ch.isLetterOrDigit() } }
            ?.let { ".$it" }
            ?: ""
        val fromTitle = title.trim()
            .map { ch -> CYRILLIC_TO_LATIN[ch] ?: ch.toString() }
            .joinToString("")
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "download" }
        return fromTitle + ext
    }

    companion object {
        private val ALLOWED_KINDS = setOf("notation_export", "other")
        private val CYRILLIC_TO_LATIN = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
            'А' to "a", 'Б' to "b", 'В' to "v", 'Г' to "g", 'Д' to "d",
            'Е' to "e", 'Ё' to "e", 'Ж' to "zh", 'З' to "z", 'И' to "i",
            'Й' to "y", 'К' to "k", 'Л' to "l", 'М' to "m", 'Н' to "n",
            'О' to "o", 'П' to "p", 'Р' to "r", 'С' to "s", 'Т' to "t",
            'У' to "u", 'Ф' to "f", 'Х' to "h", 'Ц' to "ts", 'Ч' to "ch",
            'Ш' to "sh", 'Щ' to "sch", 'Ъ' to "", 'Ы' to "y", 'Ь' to "",
            'Э' to "e", 'Ю' to "yu", 'Я' to "ya"
        )
    }
}
