package ru.kavader.arepos.controller

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.site.CreateRoadmapMilestoneRequest
import ru.kavader.arepos.dto.site.RoadmapMilestoneResponse
import ru.kavader.arepos.dto.site.ReorderRoadmapMilestonesRequest
import ru.kavader.arepos.dto.site.SetRoadmapMilestoneItemsRequest
import ru.kavader.arepos.dto.site.UpdateRoadmapMilestoneRequest
import ru.kavader.arepos.service.RoadmapService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/roadmap")
class RoadmapController(
    private val roadmapService: RoadmapService
) {
    @GetMapping
    fun list(): List<RoadmapMilestoneResponse> = roadmapService.list()

    @GetMapping("/milestones/{id}")
    fun get(
        @PathVariable id: UUID,
        @RequestParam(required = false) include: String?
    ): RoadmapMilestoneResponse = roadmapService.get(id, include)

    @PostMapping("/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateRoadmapMilestoneRequest): RoadmapMilestoneResponse =
        roadmapService.create(request)

    @PutMapping("/milestones/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateRoadmapMilestoneRequest
    ): RoadmapMilestoneResponse = roadmapService.update(id, request)

    @PutMapping("/milestones/order")
    fun reorder(@RequestBody request: ReorderRoadmapMilestonesRequest): List<RoadmapMilestoneResponse> =
        roadmapService.reorder(request)

    @DeleteMapping("/milestones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        roadmapService.delete(id)
    }

    @PutMapping("/milestones/{id}/items")
    fun setItems(
        @PathVariable id: UUID,
        @RequestBody request: SetRoadmapMilestoneItemsRequest
    ): RoadmapMilestoneResponse = roadmapService.setItems(id, request)
}
