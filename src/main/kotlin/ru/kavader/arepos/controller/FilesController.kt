package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.file.*
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import jakarta.validation.Valid
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.DocumentRefsService
import ru.kavader.arepos.service.FileStorageService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*


@RestController
@RequestMapping("/api/v1/files")
@ConditionalOnBean(FileStorageService::class)
@Tag(name = "Files", description = "File upload, download and versioning endpoints")
class FilesController(
    private val fileStorageService: FileStorageService,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val documentRefsService: DocumentRefsService
) {
    companion object {
        internal fun buildInlineContentDisposition(filename: String): String {
            val safeName = filename
                .replace(Regex("[\\r\\n\\u0000-\\u001F\\u007F]"), "")
                .replace("\"", "")
                .ifBlank { "file" }
                .take(255)
            val encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20")
            return "inline; filename=\"$safeName\"; filename*=UTF-8''$encoded"
        }
    }


    @PostMapping("/upload")
    @Operation(summary = "Upload binary file")
    fun upload(@RequestParam("file") file: MultipartFile): FileUploadResponse {
        val userId = accessService.currentUserId()
        val owner = usersRepository.findById(userId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
        val saved = fileStorageService.upload(file, owner)
        val url = "/api/v1/files/${saved.id}"
        return FileUploadResponse(
            id = saved.id!!,
            url = url,
            filename = saved.filename,
            contentType = saved.contentType,
            size = saved.size
        )
    }

    @PostMapping("/upload-markdown")
    @Operation(summary = "Upload markdown content as file")
    fun uploadMarkdown(@RequestBody @Valid request: UploadMarkdownRequest): FileUploadResponse {
        val userId = accessService.currentUserId()
        val owner = usersRepository.findById(userId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
        val saved = fileStorageService.uploadMarkdown(request.content, request.filename, owner)
        val url = "/api/v1/files/${saved.id}"
        return FileUploadResponse(
            id = saved.id!!,
            url = url,
            filename = saved.filename,
            contentType = saved.contentType,
            size = saved.size
        )
    }

    @PutMapping("/{id}/markdown")
    @Operation(summary = "Update markdown file content")
    fun updateMarkdown(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UploadMarkdownRequest
    ): FileUploadResponse {
        val userId = accessService.currentUserId()
        val owner = usersRepository.findById(userId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
        val file = fileStorageService.getFileMetadata(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        accessService.requireCanViewFile(file)
        documentRefsService.requireCanModifyMarkdownForLinkedEntities(id)
        val updated = fileStorageService.updateMarkdown(id, request.content, owner)
        val url = "/api/v1/files/${updated.id}"
        return FileUploadResponse(
            id = updated.id!!,
            url = url,
            filename = updated.filename,
            contentType = updated.contentType,
            size = updated.size
        )
    }

    @GetMapping("/{id}")
    @Operation(summary = "Download file by id")
    fun getFile(@PathVariable id: UUID): ResponseEntity<org.springframework.core.io.Resource> {
        val fileMetadata = fileStorageService.getFileMetadata(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        accessService.requireCanViewFile(fileMetadata)

        val (file, resource) = fileStorageService.getFile(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, buildInlineContentDisposition(file.filename))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .body(resource)
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "List file versions")
    fun listVersions(@PathVariable id: UUID): ListResponse<FileVersionResponse> {
        val file = fileStorageService.getFileMetadata(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        accessService.requireCanViewFile(file)
        val versions = fileStorageService.listVersions(id)
        return versions.map { version ->
            FileVersionResponse(
                versionNumber = version.versionNumber,
                createdAt = version.createdAt,
                createdBy = version.createdBy,
                size = version.size
            )
        }.toListResponse()
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    @Operation(summary = "Download specific file version")
    fun getFileVersion(
        @PathVariable id: UUID,
        @PathVariable versionNumber: Int
    ): ResponseEntity<org.springframework.core.io.Resource> {
        val fileMetadata = fileStorageService.getFileMetadata(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File or version not found")
        accessService.requireCanViewFile(fileMetadata)

        val (file, resource) = fileStorageService.getFileVersion(id, versionNumber)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File or version not found")
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, buildInlineContentDisposition(file.filename))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .body(resource)
    }
}
