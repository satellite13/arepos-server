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
import ru.kavader.arepos.dto.site.MergeFeedbackRequest
import ru.kavader.arepos.dto.site.MergeFeedbackResponse
import ru.kavader.arepos.dto.site.UpdateFeedbackRequest
import ru.kavader.arepos.mapper.AuditMapper
import ru.kavader.arepos.model.FeedbackComment
import ru.kavader.arepos.model.FeedbackItem
import ru.kavader.arepos.model.FeedbackVote
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.AuditLogRepository
import ru.kavader.arepos.repository.FeedbackCommentRepository
import ru.kavader.arepos.repository.FeedbackItemRepository
import ru.kavader.arepos.repository.FeedbackVoteRepository
import ru.kavader.arepos.repository.RoadmapMilestoneItemRepository
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
    private val roadmapMilestoneItemRepository: RoadmapMilestoneItemRepository,
    private val auditLogRepository: AuditLogRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val auditMapper: AuditMapper
) {
    fun list(type: String?, status: String?, q: String?, sort: String?, page: Int, size: Int): Page<FeedbackItemResponse> {
        validateOptionalType(type)
        validateOptionalStatus(status)
        val query = q?.trim()?.takeIf { it.isNotEmpty() }?.let(::escapeLikeQuery)
        val sortSpec = when (sort) {
            "recent", null -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
            "votes" -> Sort.by(Sort.Direction.DESC, "voteCount")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                .and(Sort.by(Sort.Direction.DESC, "id"))
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort")
        }
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), sortSpec)
        val currentUserId = CurrentUser.getId()
        return feedbackItemRepository.findByFilters(type, status, query, pageable).map { item ->
            toResponse(item, includeComments = false, currentUserId = currentUserId)
        }
    }

    private fun escapeLikeQuery(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun get(id: UUID, include: String?): FeedbackItemResponse {
        val item = findItem(id)
        val includeAudit = include?.split(",")?.any { it.trim() == "audit" } == true
        if (includeAudit) {
            accessService.requireCanManageFeedback()
        }
        return toResponse(
            item,
            includeComments = true,
            currentUserId = CurrentUser.getId(),
            includeAudit = includeAudit
        )
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
        val item = findItemForUpdate(id)
        requireNotMerged(item)
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
        request.type?.let { type ->
            if (!isAdmin) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can change type")
            }
            item.type = normalizeType(type)
        }
        item.updatedAt = Instant.now()
        return toResponse(feedbackItemRepository.save(item), includeComments = true, currentUserId = CurrentUser.getId())
    }

    @Transactional
    fun vote(id: UUID): FeedbackItemResponse {
        accessService.requireCanVoteFeedback()
        val item = findItemForUpdate(id)
        requireNotMerged(item)
        val user = currentUser()
        if (!feedbackVoteRepository.existsByItemIdAndUserId(item.id!!, user.id!!)) {
            feedbackVoteRepository.save(
                FeedbackVote(
                    item = item,
                    user = user,
                    createdAt = Instant.now()
                )
            )
            item.voteCount = feedbackVoteRepository.countByItemId(item.id!!)
            item.updatedAt = Instant.now()
            feedbackItemRepository.save(item)
        }
        return toResponse(item, includeComments = false, currentUserId = user.id)
    }

    @Transactional
    fun unvote(id: UUID): FeedbackItemResponse {
        accessService.requireCanVoteFeedback()
        val item = findItemForUpdate(id)
        requireNotMerged(item)
        val userId = accessService.currentUserId()
        feedbackVoteRepository.findByItemIdAndUserId(item.id!!, userId).ifPresent { vote ->
            feedbackVoteRepository.delete(vote)
            item.voteCount = feedbackVoteRepository.countByItemId(item.id!!)
            item.updatedAt = Instant.now()
            feedbackItemRepository.save(item)
        }
        return toResponse(item, includeComments = false, currentUserId = userId)
    }

    @Transactional
    fun addComment(id: UUID, request: CreateFeedbackCommentRequest): FeedbackCommentResponse {
        accessService.requireCanCommentFeedback()
        val item = findItemForUpdate(id)
        requireNotMerged(item)
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

    @Transactional
    fun delete(id: UUID) {
        val item = findItemForUpdate(id)
        if (!accessService.canManageFeedback()) {
            accessService.requireCanDeleteOwnFeedback(item.author.id!!, item.status)
        }
        if (feedbackItemRepository.existsByMergedIntoId(item.id!!)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete feedback with merged sources")
        }
        feedbackItemRepository.delete(item)
    }

    @Transactional
    fun merge(sourceId: UUID, request: MergeFeedbackRequest): MergeFeedbackResponse {
        accessService.requireCanManageFeedback()
        if (sourceId == request.targetId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot merge feedback into itself")
        }
        val lockedItems = feedbackItemRepository.findAllByIdInForUpdate(
            listOf(sourceId, request.targetId).sorted()
        )
        val itemsById = lockedItems.associateBy { it.id!! }
        val source = itemsById[sourceId]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback item not found")
        if (source.mergedInto != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback is already merged")
        }
        val target = itemsById[request.targetId]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback item not found")
        if (target.mergedInto != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot merge into feedback that is already merged")
        }
        val now = Instant.now()

        feedbackVoteRepository.findByItemId(source.id!!).forEach { vote ->
            if (feedbackVoteRepository.existsByItemIdAndUserId(target.id!!, vote.user.id!!)) {
                feedbackVoteRepository.delete(vote)
            } else {
                vote.item = target
                feedbackVoteRepository.save(vote)
            }
        }
        feedbackCommentRepository.findByItemIdOrderByCreatedAtAsc(source.id!!).forEach { comment ->
            comment.item = target
            feedbackCommentRepository.save(comment)
        }
        roadmapMilestoneItemRepository.findByFeedbackItemId(source.id!!).forEach { link ->
            if (roadmapMilestoneItemRepository.existsByMilestoneIdAndFeedbackItemId(link.milestone.id!!, target.id!!)) {
                roadmapMilestoneItemRepository.delete(link)
            } else {
                link.feedbackItem = target
                roadmapMilestoneItemRepository.save(link)
            }
        }

        target.voteCount = feedbackVoteRepository.countByItemId(target.id!!)
        target.updatedAt = now
        source.voteCount = feedbackVoteRepository.countByItemId(source.id!!)
        source.mergedInto = target
        source.mergedAt = now
        source.status = "declined"
        source.updatedAt = now
        feedbackItemRepository.save(target)
        feedbackItemRepository.save(source)
        return MergeFeedbackResponse(
            sourceId = source.id!!,
            targetId = target.id!!,
            mergedAt = now,
            target = toResponse(target, includeComments = true, currentUserId = CurrentUser.getId())
        )
    }

    private fun findItem(id: UUID): FeedbackItem =
        feedbackItemRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback item not found") }

    private fun findItemForUpdate(id: UUID): FeedbackItem =
        feedbackItemRepository.findByIdForUpdate(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback item not found")

    private fun requireNotMerged(item: FeedbackItem) {
        if (item.mergedInto != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Feedback has been merged")
        }
    }

    private fun currentUser(): Users =
        usersRepository.findById(accessService.currentUserId())
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

    private fun toResponse(
        item: FeedbackItem,
        includeComments: Boolean,
        currentUserId: UUID?,
        includeAudit: Boolean = false
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
            updatedAt = item.updatedAt,
            mergedIntoId = item.mergedInto?.id,
            mergedAt = item.mergedAt,
            audit = if (includeAudit) {
                auditLogRepository.findByTableNameAndRowId("feedback_items", item.id!!, PageRequest.of(0, 100))
                    .content
                    .map(auditMapper::toResponse)
            } else {
                emptyList()
            }
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
