package ru.kavader.arepos.controller

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.dto.site.CreateFeedbackCommentRequest
import ru.kavader.arepos.dto.site.CreateFeedbackRequest
import ru.kavader.arepos.dto.site.FeedbackCommentResponse
import ru.kavader.arepos.dto.site.FeedbackItemResponse
import ru.kavader.arepos.dto.site.MergeFeedbackRequest
import ru.kavader.arepos.dto.site.MergeFeedbackResponse
import ru.kavader.arepos.dto.site.UpdateFeedbackRequest
import ru.kavader.arepos.service.FeedbackService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/feedback")
class FeedbackController(
    private val feedbackService: FeedbackService
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false, defaultValue = "votes") sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ListResponse<FeedbackItemResponse> =
        feedbackService.list(type, status, q, sort, page, size).toListResponse()

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
        @RequestParam(required = false) include: String?
    ): FeedbackItemResponse = feedbackService.get(id, include)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateFeedbackRequest): FeedbackItemResponse =
        feedbackService.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateFeedbackRequest
    ): FeedbackItemResponse = feedbackService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        feedbackService.delete(id)
    }

    @PostMapping("/{id}/merge")
    fun merge(
        @PathVariable id: UUID,
        @RequestBody request: MergeFeedbackRequest
    ): MergeFeedbackResponse = feedbackService.merge(id, request)

    @PostMapping("/{id}/votes")
    fun vote(@PathVariable id: UUID): FeedbackItemResponse = feedbackService.vote(id)

    @DeleteMapping("/{id}/votes")
    fun unvote(@PathVariable id: UUID): FeedbackItemResponse = feedbackService.unvote(id)

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun comment(
        @PathVariable id: UUID,
        @RequestBody request: CreateFeedbackCommentRequest
    ): FeedbackCommentResponse = feedbackService.addComment(id, request)
}
