package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.site.CreateRoadmapMilestoneRequest
import ru.kavader.arepos.dto.site.RoadmapLinkedItemResponse
import ru.kavader.arepos.dto.site.RoadmapMilestoneResponse
import ru.kavader.arepos.dto.site.SetRoadmapMilestoneItemsRequest
import ru.kavader.arepos.dto.site.UpdateRoadmapMilestoneRequest
import ru.kavader.arepos.model.RoadmapMilestone
import ru.kavader.arepos.model.RoadmapMilestoneItem
import ru.kavader.arepos.repository.FeedbackItemRepository
import ru.kavader.arepos.repository.RoadmapMilestoneItemRepository
import ru.kavader.arepos.repository.RoadmapMilestoneRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@Service
class RoadmapService(
    private val milestoneRepository: RoadmapMilestoneRepository,
    private val milestoneItemRepository: RoadmapMilestoneItemRepository,
    private val feedbackItemRepository: FeedbackItemRepository,
    private val accessService: ResourceAccessService
) {
    fun list(): List<RoadmapMilestoneResponse> =
        milestoneRepository.findAllByOrderBySortOrderAsc().map(::toResponse)

    fun get(id: UUID): RoadmapMilestoneResponse = toResponse(findMilestone(id))

    @Transactional
    fun create(request: CreateRoadmapMilestoneRequest): RoadmapMilestoneResponse {
        accessService.requireCanManageRoadmap()
        val title = request.title.trim()
        if (title.isEmpty() || title.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid title")
        }
        val now = Instant.now()
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
        val milestone = findMilestone(id)
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
        if (request.targetPeriod != null) {
            milestone.targetPeriod = request.targetPeriod.trim().takeIf { it.isNotEmpty() }
        }
        milestone.updatedAt = Instant.now()
        return toResponse(milestoneRepository.save(milestone))
    }

    @Transactional
    fun delete(id: UUID) {
        accessService.requireCanManageRoadmap()
        val milestone = findMilestone(id)
        milestoneRepository.delete(milestone)
    }

    @Transactional
    fun setItems(id: UUID, request: SetRoadmapMilestoneItemsRequest): RoadmapMilestoneResponse {
        accessService.requireCanManageRoadmap()
        val milestone = findMilestone(id)
        milestoneItemRepository.deleteByMilestoneId(milestone.id!!)
        val uniqueIds = request.feedbackItemIds.distinct()
        for (feedbackId in uniqueIds) {
            val item = feedbackItemRepository.findById(feedbackId)
                .orElseThrow {
                    ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback item not found: $feedbackId")
                }
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

    private fun toResponse(milestone: RoadmapMilestone): RoadmapMilestoneResponse {
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
                    type = item.type,
                    title = item.title,
                    status = item.status,
                    voteCount = item.voteCount
                )
            },
            createdAt = milestone.createdAt,
            updatedAt = milestone.updatedAt
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
