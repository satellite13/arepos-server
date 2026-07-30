package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.site.CreateRoadmapMilestoneRequest
import ru.kavader.arepos.dto.site.RoadmapLinkedItemResponse
import ru.kavader.arepos.dto.site.RoadmapConflictException
import ru.kavader.arepos.dto.site.RoadmapConflictItem
import ru.kavader.arepos.dto.site.RoadmapMilestoneResponse
import ru.kavader.arepos.dto.site.ReorderRoadmapMilestonesRequest
import ru.kavader.arepos.dto.site.SetRoadmapMilestoneItemsRequest
import ru.kavader.arepos.dto.site.UpdateRoadmapMilestoneRequest
import ru.kavader.arepos.mapper.AuditMapper
import ru.kavader.arepos.model.RoadmapMilestone
import ru.kavader.arepos.model.RoadmapMilestoneItem
import ru.kavader.arepos.repository.AuditLogRepository
import ru.kavader.arepos.repository.FeedbackItemRepository
import ru.kavader.arepos.repository.RoadmapMilestoneItemRepository
import ru.kavader.arepos.repository.RoadmapMilestoneRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.util.FeedbackPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class RoadmapService(
    private val milestoneRepository: RoadmapMilestoneRepository,
    private val milestoneItemRepository: RoadmapMilestoneItemRepository,
    private val feedbackItemRepository: FeedbackItemRepository,
    private val accessService: ResourceAccessService,
    private val auditLogRepository: AuditLogRepository,
    private val auditMapper: AuditMapper
) {
    fun list(): List<RoadmapMilestoneResponse> =
        milestoneRepository.findAllByOrderBySortOrderAsc().map(::toResponse)

    fun get(id: UUID, include: String?): RoadmapMilestoneResponse {
        val includeAudit = include?.split(",")?.any { it.trim() == "audit" } == true
        if (includeAudit) {
            accessService.requireCanManageRoadmap()
        }
        return toResponse(findMilestone(id), includeAudit)
    }

    @Transactional
    fun create(request: CreateRoadmapMilestoneRequest): RoadmapMilestoneResponse {
        accessService.requireCanManageRoadmap()
        val title = request.title.trim()
        if (title.isEmpty() || title.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid title")
        }
        val now = nowMicros()
        val saved = milestoneRepository.save(
            RoadmapMilestone(
                title = title,
                description = request.description.trim(),
                status = normalizeStatus(request.status),
                sortOrder = request.sortOrder,
                targetPeriod = request.targetPeriod?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now
            )
        )
        return toResponse(saved)
    }

    @Transactional
    fun update(id: UUID, request: UpdateRoadmapMilestoneRequest): RoadmapMilestoneResponse {
        accessService.requireCanManageRoadmap()
        val milestone = findMilestoneForUpdate(id)
        requireCurrentTimestamp(milestone, request.baseUpdatedAt)
        request.title?.let {
            val title = it.trim()
            if (title.isEmpty() || title.length > 200) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid title")
            }
            milestone.title = title
        }
        request.description?.let { milestone.description = it.trim() }
        request.status?.let { milestone.status = normalizeStatus(it) }
        request.sortOrder?.let { milestone.sortOrder = it }
        request.targetPeriod?.let { targetPeriod ->
            milestone.targetPeriod = if (targetPeriod.isNull) {
                null
            } else if (targetPeriod.isTextual) {
                targetPeriod.asText().trim().takeIf { it.isNotEmpty() }
            } else {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target period")
            }
        }
        milestone.updatedAt = nowMicros()
        return toResponse(milestoneRepository.save(milestone))
    }

    @Transactional
    fun reorder(request: ReorderRoadmapMilestonesRequest): List<RoadmapMilestoneResponse> {
        accessService.requireCanManageRoadmap()
        validateReorderRequest(request)

        val requestedIds = request.items.map { it.id }
        val milestones = milestoneRepository.findAllByIdInForUpdate(requestedIds)
        val milestonesById = milestones.associateBy { requireNotNull(it.id) }
        val missingIds = requestedIds.filterNot(milestonesById::containsKey)
        if (missingIds.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Milestone not found: ${missingIds.joinToString()}"
            )
        }

        val conflicts = request.items.mapNotNull { item ->
            val milestone = requireNotNull(milestonesById[item.id])
            if (isStale(milestone.updatedAt, item.baseUpdatedAt)) {
                RoadmapConflictItem(item.id, milestone.updatedAt)
            } else {
                null
            }
        }
        if (conflicts.isNotEmpty()) {
            throw RoadmapConflictException("ROADMAP_ORDER_CONFLICT", conflicts)
        }

        val now = nowMicros()
        request.items.forEach { item ->
            val milestone = requireNotNull(milestonesById[item.id])
            milestone.sortOrder = item.sortOrder
            milestone.updatedAt = now
        }
        milestoneRepository.saveAll(milestones)
        return milestoneRepository.findAllByOrderBySortOrderAsc().map(::toResponse)
    }

    @Transactional
    fun delete(id: UUID) {
        accessService.requireCanManageRoadmap()
        val milestone = findMilestoneForUpdate(id)
        milestoneRepository.delete(milestone)
    }

    @Transactional
    fun setItems(id: UUID, request: SetRoadmapMilestoneItemsRequest): RoadmapMilestoneResponse {
        accessService.requireCanManageRoadmap()
        val milestone = milestoneRepository.findByIdForUpdate(id)
            ?: throw RoadmapConflictException(
                "ROADMAP_MILESTONE_DELETED",
                listOf(RoadmapConflictItem(id, null))
            )
        val uniqueIds = request.feedbackItemIds.distinct().sorted()
        val items = if (uniqueIds.isEmpty()) {
            emptyList()
        } else {
            feedbackItemRepository.findAllByIdInForUpdate(uniqueIds)
        }
        if (items.size != uniqueIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback item not found")
        }
        if (items.any { it.mergedInto != null }) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot link merged feedback")
        }

        // Diff instead of delete-all + insert: Hibernate flush order can INSERT before DELETE
        // and trip roadmap_milestone_items_uq for overlapping feedback ids.
        val existing = milestoneItemRepository.findByMilestoneId(milestone.id!!)
        val existingByFeedbackId = existing.associateBy { requireNotNull(it.feedbackItem.id) }
        val desiredIds = uniqueIds.toSet()
        existing
            .filter { requireNotNull(it.feedbackItem.id) !in desiredIds }
            .forEach(milestoneItemRepository::delete)
        items
            .filter { requireNotNull(it.id) !in existingByFeedbackId }
            .forEach { item ->
                milestoneItemRepository.save(
                    RoadmapMilestoneItem(
                        milestone = milestone,
                        feedbackItem = item
                    )
                )
            }
        return toResponse(milestone)
    }

    private fun findMilestone(id: UUID): RoadmapMilestone =
        milestoneRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found") }

    private fun findMilestoneForUpdate(id: UUID): RoadmapMilestone =
        milestoneRepository.findByIdForUpdate(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found")

    private fun requireCurrentTimestamp(milestone: RoadmapMilestone, baseUpdatedAt: Instant?) {
        if (baseUpdatedAt == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUpdatedAt is required")
        }
        if (isStale(milestone.updatedAt, baseUpdatedAt)) {
            throw RoadmapConflictException(
                "ROADMAP_UPDATE_CONFLICT",
                listOf(RoadmapConflictItem(requireNotNull(milestone.id), milestone.updatedAt))
            )
        }
    }

    private fun validateReorderRequest(request: ReorderRoadmapMilestonesRequest) {
        val ids = request.items.map { it.id }
        if (ids.size != ids.toSet().size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate milestone IDs")
        }
        val sortOrders = request.items.map { it.sortOrder }
        if (sortOrders.size != sortOrders.toSet().size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate milestone sort orders")
        }
        if (request.items.any { it.baseUpdatedAt == null }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUpdatedAt is required for every milestone")
        }
    }

    private fun isStale(serverUpdatedAt: Instant?, baseUpdatedAt: Instant?): Boolean =
        serverUpdatedAt == null ||
            baseUpdatedAt == null ||
            normalizePostgresTimestamp(serverUpdatedAt) != normalizePostgresTimestamp(baseUpdatedAt)

    /**
     * PostgreSQL `timestamptz` stores microseconds (values are rounded on write).
     * Compare after the same rounding so client timestamps from JSON do not false-conflict.
     */
    private fun normalizePostgresTimestamp(timestamp: Instant): Instant {
        val nanos = timestamp.nano.toLong()
        val roundedNanos = ((nanos + 500) / 1_000) * 1_000
        return if (roundedNanos >= 1_000_000_000L) {
            Instant.ofEpochSecond(timestamp.epochSecond + 1, 0)
        } else {
            Instant.ofEpochSecond(timestamp.epochSecond, roundedNanos)
        }
    }

    /** Store the same microsecond precision the DB will persist. */
    private fun nowMicros(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun toResponse(
        milestone: RoadmapMilestone,
        includeAudit: Boolean = false
    ): RoadmapMilestoneResponse {
        val links = milestoneItemRepository.findByMilestoneId(milestone.id!!)
        return RoadmapMilestoneResponse(
            id = milestone.id!!,
            title = milestone.title,
            description = milestone.description,
            status = milestone.status,
            sortOrder = milestone.sortOrder,
            targetPeriod = milestone.targetPeriod,
            items = links.map { link ->
                val item = link.feedbackItem
                RoadmapLinkedItemResponse(
                    id = item.id!!,
                    publicKey = FeedbackPublicKey.format(item.publicNumber),
                    type = item.type,
                    title = item.title,
                    status = item.status,
                    voteCount = item.voteCount
                )
            },
            createdAt = milestone.createdAt,
            updatedAt = milestone.updatedAt,
            audit = if (includeAudit) {
                auditLogRepository.findByTableNameAndRowId(
                    "roadmap_milestones",
                    milestone.id!!,
                    PageRequest.of(0, 100)
                )
                    .content
                    .map(auditMapper::toResponse)
            } else {
                emptyList()
            }
        )
    }

    private fun normalizeStatus(status: String): String {
        val normalized = status.trim().lowercase()
        if (normalized !in ALLOWED_STATUSES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status")
        }
        return normalized
    }

    companion object {
        private val ALLOWED_STATUSES = setOf("planned", "in_progress", "done")
    }
}
