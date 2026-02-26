package ru.kavader.arepos.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.FileStorageService
import java.util.*

data class FileUploadResponse(
    val id: UUID,
    val url: String,
    val filename: String,
    val contentType: String,
    val size: Long
)

data class FileVersionResponse(
    val versionNumber: Int,
    val createdAt: java.time.Instant,
    val createdBy: UUID,
    val size: Long
)

data class UploadMarkdownRequest(
    val content: String,
    val filename: String = "documentation.md"
)

@RestController
@RequestMapping("/api/v1/files")
@ConditionalOnBean(FileStorageService::class)
class FilesController(
    private val fileStorageService: FileStorageService,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {

    @PostMapping("/upload")
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
    fun uploadMarkdown(@RequestBody request: UploadMarkdownRequest): FileUploadResponse {
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
    fun updateMarkdown(
        @PathVariable id: UUID,
        @RequestBody request: UploadMarkdownRequest
    ): FileUploadResponse {
        val userId = accessService.currentUserId()
        val owner = usersRepository.findById(userId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            }
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
    fun getFile(@PathVariable id: UUID): ResponseEntity<org.springframework.core.io.Resource> {
        val (file, resource) = fileStorageService.getFile(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        accessService.requireCanViewFile(file)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${file.filename}\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .body(resource)
    }

    @GetMapping("/{id}/versions")
    fun listVersions(@PathVariable id: UUID): List<FileVersionResponse> {
        val versions = fileStorageService.listVersions(id)
        return versions.map { version ->
            FileVersionResponse(
                versionNumber = version.versionNumber,
                createdAt = version.createdAt,
                createdBy = version.createdBy,
                size = version.size
            )
        }
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    fun getFileVersion(
        @PathVariable id: UUID,
        @PathVariable versionNumber: Int
    ): ResponseEntity<org.springframework.core.io.Resource> {
        val (file, resource) = fileStorageService.getFileVersion(id, versionNumber)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File or version not found")
        accessService.requireCanViewFile(file)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${file.filename}\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .body(resource)
    }
}
