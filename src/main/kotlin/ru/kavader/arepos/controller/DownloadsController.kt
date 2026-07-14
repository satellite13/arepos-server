package ru.kavader.arepos.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import ru.kavader.arepos.dto.site.DownloadAssetResponse
import ru.kavader.arepos.service.DownloadsService
import ru.kavader.arepos.service.FileStorageService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/api/v1/downloads")
@ConditionalOnBean(FileStorageService::class)
class DownloadsController(
    private val downloadsService: DownloadsService
) {
    @GetMapping
    fun listPublished(): List<DownloadAssetResponse> = downloadsService.listPublished()

    @GetMapping("/admin")
    fun listAll(): List<DownloadAssetResponse> = downloadsService.listAll()

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestParam title: String,
        @RequestParam(defaultValue = "") description: String,
        @RequestParam kind: String,
        @RequestParam(required = false) versionLabel: String?,
        @RequestParam(defaultValue = "0") sortOrder: Int,
        @RequestParam(defaultValue = "true") published: Boolean,
        @RequestParam file: MultipartFile
    ): DownloadAssetResponse =
        downloadsService.create(title, description, kind, versionLabel, sortOrder, published, file)

    @PutMapping("/{id}/published")
    fun setPublished(
        @PathVariable id: UUID,
        @RequestParam published: Boolean
    ): DownloadAssetResponse = downloadsService.setPublished(id, published)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        downloadsService.delete(id)
    }

    @GetMapping("/{id}/file")
    fun download(@PathVariable id: UUID): ResponseEntity<org.springframework.core.io.Resource> {
        val (asset, resource) = downloadsService.download(id)
        val safeName = asset.fileName
            .replace(Regex("[\\r\\n\\u0000-\\u001F\\u007F]"), "")
            .replace("\"", "")
            .ifBlank { "download" }
            .take(255)
        val encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20")
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$safeName\"; filename*=UTF-8''$encoded")
            .contentType(MediaType.parseMediaType(asset.contentType))
            .body(resource)
    }
}
