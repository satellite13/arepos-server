package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.comment.CommentMessageResponse
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.DiagramCommentService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/comments")
@Tag(name = "Comments", description = "Operations on individual comments")
class CommentsController(
    private val commentService: DiagramCommentService,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {
    @PatchMapping("/{id}")
    @Operation(summary = "Edit a comment (author only)")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: ru.kavader.arepos.dto.comment.CommentUpdateRequest
    ): CommentMessageResponse = commentService.updateComment(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a comment (author or admin)")
    fun delete(@PathVariable id: UUID) = commentService.deleteComment(id)

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve a comment thread")
    fun resolve(@PathVariable id: UUID) = commentService.resolve(id)

    @PostMapping("/{id}/unresolve")
    @Operation(summary = "Reopen a comment thread")
    fun unresolve(@PathVariable id: UUID) = commentService.unresolve(id)

    @PutMapping("/{id}/reactions/{emoji}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add an emoji reaction")
    fun addReaction(@PathVariable id: UUID, @PathVariable emoji: String) =
        commentService.addReaction(id, emoji)

    @DeleteMapping("/{id}/reactions/{emoji}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an emoji reaction")
    fun removeReaction(@PathVariable id: UUID, @PathVariable emoji: String) =
        commentService.removeReaction(id, emoji)
}
