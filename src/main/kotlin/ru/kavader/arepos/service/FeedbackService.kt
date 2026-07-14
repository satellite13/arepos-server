package ru.kavader.arepos.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.site.CreateFeedbackCommentRequest
import ru.kavader.arepos.dto.site.CreateFeedbackRequest
import ru.kavader.arepos.dto.site.FeedbackAuthorResponse
import ru.kavader.arepos.dto.site.FeedbackCommentResponse
import ru.kavader.arepos.dto.site.FeedbackItemResponse
import ru.kavader.arepos.dto.site.UpdateFeedbackRequest
import ru.kavader.arepos.model.FeedbackComment
import ru.kavader.arepos.model.FeedbackItem
import ru.kavader.arepos.model.FeedbackVote
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.FeedbackCommentRepository
import ru.kavader.arepos.repository.FeedbackItemRepository
import ru.kavader.arepos.repository.FeedbackVoteRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class FeedbackService(
    private val feedbackItemRepository: FeedbackItemRepository,
    private val feedbackVoteRepository: FeedbackVoteRepository,
    private val feedbackCommentRepository: FeedbackCommentRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {
    fun list(type: String?, status: String?, sort: String?, page: Int, size: Int): Page<FeedbackItemResponse> {
        validateOptionalType(type)
        validateOptionalStatus(status)
        val sortSpec = when (sort) {
            "recent", null -> Sort.by(Sort.Direction.DESC, "createdAt")
            "votes" -> Sort.by(Sort.Direction.DESC, "voteCount").and(Sort.by(Sort.Direction.DESC, "createdAt"))
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort")
        }
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), sortSpec)
        val currentUserId = CurrentUser.getId()
        return feedbackItemRepository.findByFilters(type, status, pageable).map { item ->
            toResponse(item, includeComments = false, currentUserId = currentUserId)
        }
    }

    fun get(id: UUID): FeedbackItemResponse {
        val item = findItem(id)
        return toResponse(item, includeComments = true, currentUserId = CurrentUser.getId())
    }

    @Transactional
    fun create(request: CreateFeedbackRequest): FeedbackItemResponse {
        accessService.requireCanCreateFeedback()
        val author = currentUser()
        val type = normalizeType(request.type)
        val title = request.title.trim()
        val body = request.body.trim()
        validateTitleBody(title, body)
        val now = Instant.now()
        val saved = feedbackItemRepository.save(
            FeedbackItem(
                type = type,
                title = title,
                body = body,
                status = "new",
                author = author,
                voteCount = 0,
                createdAt = now,
                updatedAt = now
            )
        )
        return toResponse(saved, includeComments = false, currentUserId = author.id)
    }

    @Transactional
    fun update(id: UUID, request: UpdateFeedbackRequest): FeedbackItemResponse {
        val item = findItem(id)
        val authorId = item.author.id!!
        val isAdmin = accessService.canManageFeedback()
        if (!isAdmin) {
            accessService.requireCanEditOwnFeedback(authorId, item.status)
        }
        request.title?.let {
            val title = it.trim()
            validateTitleBody(title, item.body)
            item.title = title
        }
        request.body?.let {
            val body = it.trim()
            validateTitleBody(item.title, body)
            item.body = body
        }
        request.status?.let { status ->
            if (!isAdmin) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can change status")
            }
            item.status = normalizeStatus(status)
        }
        item.updatedAt = Instant.now()
        return toResponse(feedbackItemRepository.save(item), includeComments = true, currentUserId = CurrentUser.getId())
    }

    @Transactional
    fun vote(id: UUID): FeedbackItemResponse {
        accessService.requireCanVoteFeedback()
        val item = findItem(id)
        val user = currentUser()
        if (!feedbackVoteRepository.existsByItemIdAndUserId(item.id!!, user.id!!)) {
            feedbackVoteRepository.save(
                FeedbackVote(
                    item = item,
                    user = user,
                    createdAt = Instant.now()
                )
            )
            item.voteCount += 1
            item.updatedAt = Instant.now()
            feedbackItemRepository.save(item)
        }
        return toResponse(item, includeComments = false, currentUserId = user.id)
    }

    @Transactional
    fun unvote(id: UUID): FeedbackItemResponse {
        accessService.requireCanVoteFeedback()
        val item = findItem(id)
        val userId = accessService.currentUserId()
        feedbackVoteRepository.findByItemIdAndUserId(item.id!!, userId).ifPresent { vote ->
            feedbackVoteRepository.delete(vote)
            item.voteCount = (item.voteCount - 1).coerceAtLeast(0)
            item.updatedAt = Instant.now()
            feedbackItemRepository.save(item)
        }
        return toResponse(item, includeComments = false, currentUserId = userId)
    }

    @Transactional
    fun addComment(id: UUID, request: CreateFeedbackCommentRequest): FeedbackCommentResponse {
        accessService.requireCanCommentFeedback()
        val item = findItem(id)
        val author = currentUser()
        val body = request.body.trim()
        if (body.isEmpty() || body.length > MAX_BODY) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid comment body")
        }
        val saved = feedbackCommentRepository.save(
            FeedbackComment(
                item = item,
                author = author,
                body = body,
                createdAt = Instant.now()
            )
        )
        return toCommentResponse(saved)
    }

    private fun findItem(id: UUID): FeedbackItem =
        feedbackItemRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback item not found") }

    private fun currentUser(): Users =
        usersRepository.findById(accessService.currentUserId())
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

    private fun toResponse(
        item: FeedbackItem,
        includeComments: Boolean,
        currentUserId: UUID?
    ): FeedbackItemResponse {
        val votedByMe = currentUserId != null &&
            feedbackVoteRepository.existsByItemIdAndUserId(item.id!!, currentUserId)
        val comments = if (includeComments) {
            feedbackCommentRepository.findByItemIdOrderByCreatedAtAsc(item.id!!).map(::toCommentResponse)
        } else {
            emptyList()
        }
        return FeedbackItemResponse(
            id = item.id!!,
            type = item.type,
            title = item.title,
            body = item.body,
            status = item.status,
            author = toAuthor(item.author),
            voteCount = item.voteCount,
            votedByMe = votedByMe,
            comments = comments,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt
        )
    }

    private fun toCommentResponse(comment: FeedbackComment): FeedbackCommentResponse =
        FeedbackCommentResponse(
            id = comment.id!!,
            body = comment.body,
            author = toAuthor(comment.author),
            createdAt = comment.createdAt
        )

    private fun toAuthor(user: Users): FeedbackAuthorResponse {
        val display = user.email.substringBefore("@")
        return FeedbackAuthorResponse(id = user.id!!, displayName = display)
    }

    private fun validateTitleBody(title: String, body: String) {
        if (title.isEmpty() || title.length > MAX_TITLE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid title")
        }
        if (body.isEmpty() || body.length > MAX_BODY) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid body")
        }
    }

    private fun validateOptionalType(type: String?) {
        if (type != null) normalizeType(type)
    }

    private fun validateOptionalStatus(status: String?) {
        if (status != null) normalizeStatus(status)
    }

    private fun normalizeType(type: String): String {
        val normalized = type.trim().lowercase()
        if (normalized !in ALLOWED_TYPES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid type")
        }
        return normalized
    }

    private fun normalizeStatus(status: String): String {
        val normalized = status.trim().lowercase()
        if (normalized !in ALLOWED_STATUSES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status")
        }
        return normalized
    }

    companion object {
        private const val MAX_TITLE = 200
        private const val MAX_BODY = 10_000
        private val ALLOWED_TYPES = setOf("idea", "bug")
        private val ALLOWED_STATUSES = setOf("new", "planned", "in_progress", "done", "declined")
    }
}
