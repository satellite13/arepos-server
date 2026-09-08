package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.comment.CommentLinkPreviewResponse
import ru.kavader.arepos.model.CommentLinkPreview
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.CommentLinkPreviewRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Unfurl ссылок для превью-карточек: Open Graph метаданные с SSRF-guard,
 * OG-картинка проксируется в S3 (same-origin отдача, CSP не меняется).
 * Кеш в comment_link_previews, TTL 7 дней.
 */
@Service
class LinkPreviewService(
    private val previewRepository: CommentLinkPreviewRepository,
    private val filesRepository: FilesRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val fileStorageProvider: ObjectProvider<FileStorageService>,
    private val objectMapper: ObjectMapper
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    @Transactional
    fun resolve(rawUrl: String): CommentLinkPreviewResponse {
        accessService.currentUserId() // требует аутентификации
        val url = normalizeUrl(rawUrl) ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Invalid URL"
        )
        val cached = previewRepository.findById(url).orElse(null)
        if (cached != null && (cached.fetchedAt ?: Instant.EPOCH).isAfter(Instant.now().minus(CACHE_TTL))) {
            return toResponse(cached)
        }
        val fetched = fetchPreview(url) ?: return toResponse(cached ?: emptyPreview(url))
        val saved = previewRepository.save(fetched)
        return toResponse(saved)
    }

    // ------------------------------------------------------------- internals

    private fun fetchPreview(url: String): CommentLinkPreview? {
        if (!isPublicHttpUrl(url)) {
            log.debug("Link preview blocked by SSRF guard: {}", url)
            return null
        }
        val html = try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(FETCH_TIMEOUT_SECONDS))
                .header("User-Agent", "wArchi-LinkPreview/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8))
            if (response.statusCode() !in 200..299) return null
            val contentType = response.headers().firstValue("content-type").orElse("")
            if (!contentType.contains("text/html") && !contentType.contains("application/xhtml")) return null
            if (response.body().length > MAX_HTML_BYTES) response.body().take(MAX_HTML_BYTES) else response.body()
        } catch (e: Exception) {
            log.debug("Link preview fetch failed for {}: {}", url, e.message)
            return null
        }

        val meta = extractOgMeta(html)
        val imageUrl = meta["og:image"] ?: meta["twitter:image"]
        val imageFile = imageUrl?.let { fetchAndStoreImage(url, it) }

        return CommentLinkPreview(
            url = url,
            title = (meta["og:title"] ?: meta["twitter:title"])?.take(PREVIEW_FIELD_MAX),
            description = (meta["og:description"] ?: meta["twitter:description"])?.take(PREVIEW_FIELD_MAX),
            siteName = (meta["og:site_name"] ?: hostOf(url))?.take(PREVIEW_FIELD_MAX),
            imageFile = imageFile,
            fetchedAt = Instant.now()
        )
    }

    private fun fetchAndStoreImage(pageUrl: String, imageUrl: String): ru.kavader.arepos.model.Files? {
        return try {
            if (!isPublicHttpUrl(imageUrl)) return null
            val request = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(FETCH_TIMEOUT_SECONDS))
                .header("User-Agent", "wArchi-LinkPreview/1.0")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) return null
            val bytes = response.body()
            if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null
            val contentType = response.headers().firstValue("content-type").orElse("")
                .substringBefore(';').trim().lowercase()
            if (contentType !in ALLOWED_IMAGE_CONTENT_TYPES) return null
            val owner = systemUser() ?: return null
            val storage = fileStorageProvider.ifAvailable ?: return null
            storage.uploadBytes(
                content = bytes,
                filename = "og-preview-${UUID.randomUUID()}",
                contentType = contentType,
                owner = owner,
                keyPrefix = "link-previews"
            )
        } catch (e: Exception) {
            log.debug("Link preview image fetch failed for {}: {}", pageUrl, e.message)
            null
        }
    }

    private fun extractOgMeta(html: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val metaRegex = Regex("""<meta\s+[^>]*?(?:property|name)\s*=\s*["']([^"']+)["'][^>]*?>""", RegexOption.IGNORE_CASE)
        for (match in metaRegex.findAll(html)) {
            val tag = match.value
            val key = match.groupValues[1].lowercase()
            if (!key.startsWith("og:") && !key.startsWith("twitter:")) continue
            val content = Regex("""content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1) ?: continue
            result.putIfAbsent(key, decodeHtmlEntities(content))
        }
        return result
    }

    private fun decodeHtmlEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")

    private fun isPublicHttpUrl(rawUrl: String): Boolean {
        return try {
            val uri = URI.create(rawUrl)
            val scheme = uri.scheme?.lowercase()
            val port = uri.port
            if (scheme !in setOf("http", "https")) return false
            if (port !in listOf(-1, 80, 443)) return false
            val host = uri.host ?: return false
            val addresses = InetAddress.getAllByName(host)
            addresses.all { addr -> isPublicAddress(addr) }
        } catch (_: Exception) {
            false
        }
    }

    private fun isPublicAddress(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress ||
            addr.isAnyLocalAddress || addr.isMulticastAddress
        ) {
            return false
        }
        val bytes = addr.address
        return when (bytes.size) {
            4 -> {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                !(
                    b0 == 0 || b0 == 10 || b0 == 127 ||
                        (b0 == 172 && b1 in 16..31) ||
                        (b0 == 192 && b1 == 168) ||
                        (b0 == 169 && b1 == 254) ||
                        b0 >= 224
                    )
            }
            16 -> {
                val b0 = bytes[0].toInt() and 0xFF
                // ULA fc00::/7 и link-local fe80::/10
                !(b0 == 0xFD || b0 == 0xFC || (b0 == 0xFE && (bytes[1].toInt() and 0xC0) == 0x80))
            }
            else -> true
        }
    }

    private fun normalizeUrl(rawUrl: String): String? = try {
        val uri = URI(rawUrl.trim())
        if (uri.scheme?.lowercase() in setOf("http", "https") && uri.host != null) {
            uri.toString().take(2048)
        } else null
    } catch (_: Exception) {
        null
    }

    private fun hostOf(url: String): String? = runCatching { URI.create(url).host }.getOrNull()

    private fun emptyPreview(url: String): CommentLinkPreview =
        CommentLinkPreview(url = url, fetchedAt = Instant.now())

    private fun toResponse(preview: CommentLinkPreview): CommentLinkPreviewResponse =
        CommentLinkPreviewResponse(
            url = preview.url,
            title = preview.title,
            description = preview.description,
            siteName = preview.siteName,
            imageUrl = preview.imageFile?.id?.let { "/api/v1/files/$it" }
        )

    private fun systemUser(): Users? {
        // Служебный владелец превью-картинок: берём текущего пользователя (он и инициировал unfurl)
        return usersRepository.findById(accessService.currentUserId()).orElse(null)
    }

    companion object {
        private val log = LoggerFactory.getLogger(LinkPreviewService::class.java)
        private val CACHE_TTL: Duration = Duration.ofDays(7)
        private const val FETCH_TIMEOUT_SECONDS: Long = 5
        private const val MAX_HTML_BYTES = 512 * 1024
        private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        private const val PREVIEW_FIELD_MAX = 300
        private val ALLOWED_IMAGE_CONTENT_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")
    }
}
