package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.comment.*
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.util.CommentMentionParser
import java.time.Instant
import java.util.UUID

@Service
class DiagramCommentService(
    private val commentRepository: DiagramCommentRepository,
    private val attachmentRepository: DiagramCommentAttachmentRepository,
    private val reactionRepository: DiagramCommentReactionRepository,
    private val readStateRepository: DiagramCommentReadStateRepository,
    private val filesRepository: FilesRepository,
    private val usersRepository: UsersRepository,
    private val diagramsRepository: DiagramsRepository,
    private val previewRepository: CommentLinkPreviewRepository,
    private val accessService: ResourceAccessService,
    private val notificationService: NotificationService,
    private val commentEventBroadcaster: CommentEventBroadcaster,
    private val autoResolveService: DiagramCommentAutoResolveService,
    private val objectMapper: ObjectMapper
) {
    // ---------------------------------------------------------------- list

    @Transactional(readOnly = true)
    fun listThreads(
        diagram: Diagrams,
        status: String?,
        targetType: String?,
        instanceId: String?
    ): CommentThreadsResponse {
        accessService.requireCanViewDiagram(diagram)
        val diagramId = requireNotNull(diagram.id)
        val viewerId = accessService.currentUserId()
        val comments = commentRepository.findByDiagramIdAndDeletedAtIsNull(diagramId)
        if (comments.isEmpty()) {
            return CommentThreadsResponse(threads = emptyList(), totalCount = 0)
        }

        val existingInstances = extractExistingInstanceIds(diagram)
        val lastReadAt = readStateRepository
            .findByKeyUserIdAndKeyDiagramId(viewerId, diagramId)?.lastReadAt ?: Instant.EPOCH

        val byThread = comments.groupBy { it.thread?.id ?: requireNotNull(it.id) }
        val roots = byThread.entries.mapNotNull { (threadId, group) ->
            val root = group.firstOrNull { it.thread == null } ?: return@mapNotNull null
            val replies = group.filter { it.thread != null }.sortedBy { it.createdAt ?: Instant.EPOCH }
            ThreadAggregate(root, replies, threadId)
        }

        val filtered = roots.filter { agg ->
            val root = agg.root
            val statusOk = when (status) {
                "active" -> !root.isResolved
                "resolved" -> root.isResolved
                else -> true
            }
            val typeOk = targetType == null || root.targetType == targetType
            val instanceOk = instanceId == null || root.instanceId == instanceId
            statusOk && typeOk && instanceOk
        }.sortedByDescending { agg -> agg.lastActivityAt }

        val threads = filtered.map { agg -> toThreadResponse(agg, existingInstances, lastReadAt, viewerId) }
        return CommentThreadsResponse(
            threads = threads,
            totalCount = comments.size.toLong()
        )
    }

    @Transactional(readOnly = true)
    fun counts(diagram: Diagrams): CommentCountResponse {
        accessService.requireCanViewDiagram(diagram)
        val diagramId = requireNotNull(diagram.id)
        val viewerId = accessService.currentUserId()
        val comments = commentRepository.findByDiagramIdAndDeletedAtIsNull(diagramId)
        val lastReadAt = readStateRepository
            .findByKeyUserIdAndKeyDiagramId(viewerId, diagramId)?.lastReadAt ?: Instant.EPOCH

        val roots = comments
            .filter { it.instanceId != null }
            .groupBy { it.thread?.id ?: requireNotNull(it.id) }
            .map { it.value.first() }
            .filter { it.instanceId != null }

        val byInstance = roots
            .groupingBy { requireNotNull(it.instanceId) }
            .eachCount()

        val byInstanceUnresolved = roots
            .filter { !it.isResolved }
            .groupingBy { requireNotNull(it.instanceId) }
            .eachCount()

        val unread = comments.count {
            it.author.id != viewerId &&
                (it.createdAt ?: Instant.EPOCH).isAfter(lastReadAt)
        }
        return CommentCountResponse(
            diagramId = diagramId,
            totalCount = comments.size.toLong(),
            unreadCount = unread.toLong(),
            byInstance = byInstance.mapValues { it.value.toLong() },
            byInstanceUnresolved = byInstanceUnresolved.mapValues { it.value.toLong() }
        )
    }

    // ------------------------------------------------------------- create

    @Transactional
    fun createComment(diagram: Diagrams, request: CommentCreateRequest): CommentThreadResponse {
        accessService.requireCanEditDiagram(diagram)
        val diagramId = requireNotNull(diagram.id)
        val authorId = accessService.currentUserId()
        val body = request.bodyMd.trim()
        if (body.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment body must not be empty")
        }
        if (body.length > MAX_BODY_LENGTH) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment body exceeds $MAX_BODY_LENGTH characters")
        }
        val targetType = normalizeTargetType(request.targetType)
        val instanceId = request.instanceId?.takeIf { it.isNotBlank() }
        if (targetType != TARGET_DIAGRAM && instanceId == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "instanceId is required for $targetType target")
        }
        val existingInstances = extractExistingInstanceIds(diagram)
        if (instanceId != null && instanceId !in existingInstances) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown canvas instance: $instanceId")
        }

        val thread: DiagramComment? = request.threadId?.let { threadId ->
            val root = commentRepository.findById(threadId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Thread $threadId not found")
            }
            if (root.diagram.id != diagramId || root.thread != null || root.deletedAt != null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid thread reference")
            }
            root
        }

        val author = usersRepository.findById(authorId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $authorId not found")
        }
        val now = Instant.now()
        val mentions = resolveMentionUserIds(body)
        val comment = DiagramComment(
            diagram = diagram,
            modelId = diagram.model.id,
            targetType = targetType,
            instanceId = instanceId,
            elementName = request.elementName?.takeIf { it.isNotBlank() }?.take(255),
            thread = thread,
            author = author,
            bodyMd = body,
            mentions = objectMapper.writeValueAsString(mentions),
            createdAt = now,
            updatedAt = now
        )
        val saved = commentRepository.save(comment)

        if (request.attachmentFileIds.isNotEmpty()) {
            if (request.attachmentFileIds.size > MAX_ATTACHMENTS) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Too many attachments; max $MAX_ATTACHMENTS"
                )
            }
            request.attachmentFileIds.distinct().forEachIndexed { index, fileId ->
                val file = filesRepository.findById(fileId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "File $fileId not found")
                }
                attachmentRepository.save(
                    DiagramCommentAttachment(comment = saved, file = file, position = index)
                )
            }
        }

        notifyParticipants(diagram, thread, saved, mentions)
        commentEventBroadcaster.broadcastCommentEvent(diagram, "created", saved)

        val existing = extractExistingInstanceIds(diagram)
        val lastReadAt = readStateRepository
            .findByKeyUserIdAndKeyDiagramId(authorId, diagramId)?.lastReadAt ?: Instant.EPOCH
        return toThreadResponse(ThreadAggregate(saved, emptyList(), requireNotNull(saved.id)), existing, lastReadAt, authorId)
    }

    // ------------------------------------------------------------- update

    @Transactional
    fun updateComment(id: UUID, request: CommentUpdateRequest): CommentMessageResponse {
        val comment = getLiveComment(id)
        val viewerId = accessService.currentUserId()
        if (comment.author.id != viewerId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can edit the comment")
        }
        request.bodyMd?.let { body ->
            val trimmed = body.trim()
            if (trimmed.isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment body must not be empty")
            }
            if (trimmed.length > MAX_BODY_LENGTH) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Comment body exceeds $MAX_BODY_LENGTH characters"
                )
            }
            comment.bodyMd = trimmed
            comment.bodyMd = trimmed
            comment.mentions = objectMapper.writeValueAsString(resolveMentionUserIds(trimmed))
            comment.editedAt = Instant.now()
        }
        if (request.attachmentFileIds != null) {
            if (request.attachmentFileIds.size > MAX_ATTACHMENTS) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Too many attachments; max $MAX_ATTACHMENTS"
                )
            }
            attachmentRepository.findByCommentIdOrderByPositionAscFileIdAsc(id)
                .forEach(attachmentRepository::delete)
            request.attachmentFileIds.distinct().forEachIndexed { index, fileId ->
                val file = filesRepository.findById(fileId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "File $fileId not found")
                }
                attachmentRepository.save(
                    DiagramCommentAttachment(comment = comment, file = file, position = index)
                )
            }
        }
        comment.updatedAt = Instant.now()
        val saved = commentRepository.save(comment)
        commentEventBroadcaster.broadcastCommentEvent(saved.diagram, "updated", saved)
        return toMessageResponse(saved, viewerId)
    }

    @Transactional
    fun deleteComment(id: UUID) {
        val comment = getLiveComment(id)
        val viewerId = accessService.currentUserId()
        if (comment.author.id != viewerId && !accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author or admin can delete the comment")
        }
        comment.deletedAt = Instant.now()
        comment.updatedAt = Instant.now()
        commentRepository.save(comment)
        commentEventBroadcaster.broadcastCommentEvent(comment.diagram, "deleted", comment)
    }

    // ------------------------------------------------------------ resolve

    @Transactional
    fun resolve(id: UUID) {
        val comment = getRootComment(id)
        accessService.requireCanEditDiagram(comment.diagram)
        comment.isResolved = true
        comment.resolvedReason = "manual"
        comment.resolvedAt = Instant.now()
        comment.resolvedBy = usersRepository.getReferenceById(accessService.currentUserId())
        comment.updatedAt = Instant.now()
        val saved = commentRepository.save(comment)
        commentEventBroadcaster.broadcastCommentEvent(saved.diagram, "resolved", saved)
    }

    @Transactional
    fun unresolve(id: UUID) {
        val comment = getRootComment(id)
        accessService.requireCanEditDiagram(comment.diagram)
        comment.isResolved = false
        comment.resolvedReason = null
        comment.resolvedAt = null
        comment.resolvedBy = null
        comment.updatedAt = Instant.now()
        val saved = commentRepository.save(comment)
        commentEventBroadcaster.broadcastCommentEvent(saved.diagram, "resolved", saved)
    }

    // ---------------------------------------------------------- reactions

    @Transactional
    fun addReaction(id: UUID, emoji: String) {
        val comment = getLiveComment(id)
        accessService.requireCanEditDiagram(comment.diagram)
        if (emoji !in ALLOWED_EMOJI) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Emoji not allowed: $emoji")
        }
        val viewerId = accessService.currentUserId()
        val key = DiagramCommentReactionKey(commentId = id, userId = viewerId, emoji = emoji)
        if (!reactionRepository.existsById(key)) {
            val user = usersRepository.getReferenceById(viewerId)
            reactionRepository.save(
                DiagramCommentReaction(
                    key = key,
                    comment = comment,
                    user = user,
                    emoji = emoji,
                    createdAt = Instant.now()
                )
            )
            commentEventBroadcaster.broadcastCommentEvent(comment.diagram, "reaction", comment)
        }
    }

    @Transactional
    fun removeReaction(id: UUID, emoji: String) {
        val comment = getLiveComment(id)
        accessService.requireCanEditDiagram(comment.diagram)
        val viewerId = accessService.currentUserId()
        val removed = reactionRepository.deleteByKey(
            DiagramCommentReactionKey(commentId = id, userId = viewerId, emoji = emoji)
        )
        if (removed > 0) {
            commentEventBroadcaster.broadcastCommentEvent(comment.diagram, "reaction", comment)
        }
    }

    // --------------------------------------------------------- read state

    @Transactional
    fun markRead(diagram: Diagrams, request: CommentReadStateRequest): CommentReadStateResponse {
        accessService.requireCanViewDiagram(diagram)
        val diagramId = requireNotNull(diagram.id)
        val viewerId = accessService.currentUserId()
        val key = DiagramCommentReadStateKey(userId = viewerId, diagramId = diagramId)
        val state = readStateRepository.findByKeyUserIdAndKeyDiagramId(viewerId, diagramId)
            ?: DiagramCommentReadState(
                key = key,
                user = usersRepository.getReferenceById(viewerId),
                diagram = diagram,
                lastReadAt = request.lastReadAt
            )
        if ((state.lastReadAt ?: Instant.EPOCH).isBefore(request.lastReadAt)) {
            state.lastReadAt = request.lastReadAt
        }
        readStateRepository.save(state)

        val unread = commentRepository.findByDiagramIdAndDeletedAtIsNull(diagramId).count {
            it.author.id != viewerId && (it.createdAt ?: Instant.EPOCH).isAfter(state.lastReadAt ?: Instant.EPOCH)
        }
        return CommentReadStateResponse(lastReadAt = state.lastReadAt ?: request.lastReadAt, unreadCount = unread.toLong())
    }

    // ------------------------------------------------------- file access

    /** Разрешает просмотр файла, если он является вложением комментария доступной модели. */
    @Transactional(readOnly = true)
    fun canViewFileViaComment(fileId: UUID): Boolean {
        val attachment = attachmentRepository.findFirstByFileId(fileId) ?: return false
        val model = attachment.comment.diagram.model
        return accessService.canViewModel(model)
    }

    // -------------------------------------------------------------- helpers

    private fun getLiveComment(id: UUID): DiagramComment {
        val comment = commentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Comment $id not found")
        }
        if (comment.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Comment $id not found")
        }
        accessService.requireCanViewDiagram(comment.diagram)
        return comment
    }

    private fun getRootComment(id: UUID): DiagramComment {
        val comment = getLiveComment(id)
        if (comment.thread != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only thread roots can be resolved")
        }
        return comment
    }

    private fun extractExistingInstanceIds(diagram: Diagrams): Set<String> =
        autoResolveService.extractNodeInstanceIds(diagram.attrs) +
            autoResolveService.extractEdgeInstanceIds(diagram.attrs)

    private fun notifyParticipants(
        diagram: Diagrams,
        thread: DiagramComment?,
        reply: DiagramComment,
        mentions: List<UUID>
    ) {
        val diagramId = requireNotNull(diagram.id)
        val actorId = reply.author.id ?: return
        val recipients = LinkedHashSet<UUID>()
        mentions.forEach { if (it != actorId) recipients.add(it) }
        val rootAuthor = thread?.author?.id
        if (rootAuthor != null && rootAuthor != actorId && rootAuthor !in recipients) {
            recipients.add(rootAuthor)
        }
        val excerpt = reply.bodyMd.take(NOTIFICATION_EXCERPT_LENGTH)
        recipients.forEach { recipientId ->
            val type = if (recipientId in mentions) "comment_mention" else "comment_reply"
            notificationService.createNotification(
                recipientId,
                type,
                mapOf(
                    "modelId" to diagram.model.id?.toString(),
                    "diagramId" to diagramId.toString(),
                    "commentId" to requireNotNull(reply.id).toString(),
                    "threadId" to (thread?.id ?: reply.id)?.toString(),
                    "actorId" to actorId.toString(),
                    "actorName" to displayName(reply.author),
                    "excerpt" to excerpt
                )
            )
        }
    }

    private fun toThreadResponse(
        agg: ThreadAggregate,
        existingInstances: Set<String>,
        lastReadAt: Instant,
        viewerId: UUID
    ): CommentThreadResponse {
        val root = agg.root
        val instanceId = root.instanceId
        val unread = (listOf(root) + agg.replies).any {
            it.author.id != viewerId && (it.createdAt ?: Instant.EPOCH).isAfter(lastReadAt)
        }
        val replyCount = agg.replies.size
        return CommentThreadResponse(
            id = requireNotNull(root.id),
            targetType = root.targetType,
            instanceId = instanceId,
            elementName = root.elementName,
            elementExists = instanceId == null || instanceId in existingInstances,
            isResolved = root.isResolved,
            resolvedAt = root.resolvedAt,
            resolvedReason = root.resolvedReason,
            lastActivityAt = agg.lastActivityAt,
            replyCount = replyCount,
            message = toMessageResponse(root, viewerId),
            replies = agg.replies.map { toMessageResponse(it, viewerId) }
        )
    }

    private fun toMessageResponse(comment: DiagramComment, viewerId: UUID): CommentMessageResponse {
        val attachments = attachmentRepository
            .findByCommentIdOrderByPositionAscFileIdAsc(requireNotNull(comment.id))
            .map {
                CommentAttachmentResponse(
                    fileId = requireNotNull(it.file.id),
                    filename = it.file.filename,
                    contentType = it.file.contentType,
                    size = it.file.size,
                    url = "/api/v1/files/${it.file.id}"
                )
            }
        val reactions = reactionRepository.findByKeyCommentId(requireNotNull(comment.id))
            .groupBy { it.emoji }
            .map { (emoji, list) ->
                CommentReactionResponse(
                    emoji = emoji,
                    count = list.size.toLong(),
                    viewerReacted = list.any { it.user.id == viewerId }
                )
            }
            .sortedByDescending { it.count }
        val preview = CommentMentionParser.extractFirstUrl(comment.bodyMd)?.let { url ->
            previewRepository.findById(url).orElse(null)
        }?.let { p ->
            CommentLinkPreviewResponse(
                url = p.url,
                title = p.title,
                description = p.description,
                siteName = p.siteName,
                imageUrl = p.imageFile?.id?.let { "/api/v1/files/$it" }
            )
        }
        return CommentMessageResponse(
            id = requireNotNull(comment.id),
            author = CommentAuthorResponse(
                id = requireNotNull(comment.author.id),
                displayName = displayName(comment.author)
            ),
            bodyMd = comment.bodyMd,
            mentions = parseMentions(comment.mentions),
            editedAt = comment.editedAt,
            deletedAt = comment.deletedAt,
            createdAt = comment.createdAt ?: Instant.now(),
            attachments = attachments,
            reactions = reactions,
            linkPreview = preview
        )
    }

    private fun parseMentions(raw: String?): List<UUID> = runCatching {
        val type = objectMapper.typeFactory.constructCollectionType(List::class.java, UUID::class.java)
        objectMapper.readValue<List<UUID>>(raw ?: "[]", type)
    }.getOrDefault(emptyList())

    /**
     * Резолвинг токенов упоминаний во внутренние user id:
     * `user:{uuid}` — прямой id, `oidc:{sub}` — через users.oidc_sub.
     */
    private fun resolveMentionUserIds(bodyMd: String): List<UUID> =
        CommentMentionParser.extractMentionTokens(bodyMd).mapNotNull { token ->
            when (token.kind) {
                "user" -> runCatching { UUID.fromString(token.value) }.getOrNull()
                "oidc" -> usersRepository.findByOidcSub(token.value)?.id
                else -> null
            }
        }.distinct()

    private fun displayName(user: Users): String = user.email.substringBefore("@")

    private class ThreadAggregate(
        val root: DiagramComment,
        val replies: List<DiagramComment>,
        val threadId: UUID
    ) {
        val lastActivityAt: Instant =
            (listOf(root) + replies).maxOf { it.createdAt ?: Instant.EPOCH }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DiagramCommentService::class.java)
        const val TARGET_DIAGRAM = "diagram"
        const val TARGET_NODE = "node"
        const val TARGET_EDGE = "edge"
        const val MAX_BODY_LENGTH = 10000
        const val MAX_ATTACHMENTS = 10
        const val NOTIFICATION_EXCERPT_LENGTH = 120

        val ALLOWED_EMOJI = setOf("👍", "👎", "✅", "❓", "🎉", "🚀", "👀")

        fun normalizeTargetType(raw: String?): String = when (raw?.lowercase()) {
            null, "", TARGET_DIAGRAM -> TARGET_DIAGRAM
            TARGET_NODE -> TARGET_NODE
            TARGET_EDGE -> TARGET_EDGE
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid targetType: $raw")
        }
    }
}
