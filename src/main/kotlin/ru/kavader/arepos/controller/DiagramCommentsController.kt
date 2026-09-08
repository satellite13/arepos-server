package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.comment.*
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.DiagramCommentService
import ru.kavader.arepos.service.FileStorageService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/diagrams/{diagramId}/comments")
@Tag(name = "Diagram Comments", description = "Comments on diagrams and canvas element instances")
class DiagramCommentsController(
    private val commentService: DiagramCommentService,
    private val diagramsRepository: DiagramsRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val fileStorageProvider: ObjectProvider<FileStorageService>
) {
    @GetMapping
    @Operation(summary = "List comment threads of a diagram")
    fun list(
        @PathVariable diagramId: UUID,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) targetType: String?,
        @RequestParam(required = false) instanceId: String?
    ): CommentThreadsResponse {
        val diagram = loadDiagram(diagramId)
        return commentService.listThreads(diagram, status, targetType, instanceId)
    }

    @GetMapping("/counts")
    @Operation(summary = "Comment counts (badges) for a diagram")
    fun counts(@PathVariable diagramId: UUID): CommentCountResponse {
        val diagram = loadDiagram(diagramId)
        return commentService.counts(diagram)
    }

    @PostMapping
    @Operation(summary = "Create a comment (thread root or reply)")
    fun create(
        @PathVariable diagramId: UUID,
        @RequestBody @Valid request: CommentCreateRequest
    ): CommentThreadResponse {
        val diagram = loadDiagram(diagramId)
        return commentService.createComment(diagram, request)
    }

    @PostMapping("/read-state")
    @Operation(summary = "Mark comments as read up to the given timestamp")
    fun markRead(
        @PathVariable diagramId: UUID,
        @RequestBody @Valid request: CommentReadStateRequest
    ): CommentReadStateResponse {
        val diagram = loadDiagram(diagramId)
        return commentService.markRead(diagram, request)
    }

    /** Загрузка вложения комментария: сразу в MinIO, привязка при создании комментария. */
    @PostMapping("/attachments", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload a comment attachment (images, Word, Excel)")
    fun uploadAttachment(
        @PathVariable diagramId: UUID,
        @RequestParam("file") file: MultipartFile
    ): FileAttachmentResponse {
        val diagram = loadDiagram(diagramId)
        accessService.requireCanEditDiagram(diagram)
        val userId = accessService.currentUserId()
        val owner = usersRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val saved = fileStorageProvider.ifAvailable?.uploadCommentAttachment(file, owner)
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is disabled")
        return FileAttachmentResponse(
            fileId = requireNotNull(saved.id),
            filename = saved.filename,
            contentType = saved.contentType,
            size = saved.size,
            url = "/api/v1/files/${saved.id}"
        )
    }

    private fun loadDiagram(diagramId: UUID) =
        diagramsRepository.findById(diagramId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $diagramId not found")
        }
}

data class FileAttachmentResponse(
    val fileId: UUID,
    val filename: String,
    val contentType: String,
    val size: Long,
    val url: String
)
