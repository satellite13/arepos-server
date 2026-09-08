package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.comment.CommentLinkPreviewResponse
import ru.kavader.arepos.dto.comment.LinkPreviewResolveRequest
import ru.kavader.arepos.service.LinkPreviewService

@RestController
@RequestMapping("/api/v1/link-previews")
@Tag(name = "Link Previews", description = "Open Graph link unfurl with caching (SSRF-guarded)")
class LinkPreviewsController(
    private val linkPreviewService: LinkPreviewService
) {
    @PostMapping("/resolve")
    @Operation(summary = "Resolve Open Graph preview for a URL (cached, 7 days TTL)")
    fun resolve(@RequestBody @Valid request: LinkPreviewResolveRequest): CommentLinkPreviewResponse =
        linkPreviewService.resolve(request.url)
}
