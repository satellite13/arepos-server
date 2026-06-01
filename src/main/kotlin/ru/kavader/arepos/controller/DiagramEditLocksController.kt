package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.model.DiagramLockStatusResponse
import ru.kavader.arepos.dto.model.DiagramPointerRequest
import ru.kavader.arepos.service.DiagramCollaborationService
import ru.kavader.arepos.service.DiagramEditLockConflictException
import ru.kavader.arepos.service.DiagramEditLockService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/diagram-locks")
class DiagramEditLocksController(
    private val diagramEditLockService: DiagramEditLockService,
    private val diagramCollaborationService: DiagramCollaborationService
) {

    /**
     * Всегда 200 + JSON: при конфликте `reason: LOCKED_BY_OTHER` (без 409), чтобы fetch в браузере
     * не засорял консоль ожидаемым сценарием «диаграмма занята».
     */
    @PostMapping("/{diagramId}/acquire")
    fun acquire(@PathVariable diagramId: UUID): DiagramLockStatusResponse {
        return try {
            diagramEditLockService.acquire(diagramId)
        } catch (ex: DiagramEditLockConflictException) {
            ex.body
        }
    }

    @PostMapping("/{diagramId}/heartbeat")
    fun heartbeat(@PathVariable diagramId: UUID): DiagramLockStatusResponse =
        diagramEditLockService.heartbeat(diagramId)

    @PostMapping("/{diagramId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun release(@PathVariable diagramId: UUID) {
        diagramEditLockService.release(diagramId)
    }

    @PostMapping("/{diagramId}/force-release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forceRelease(@PathVariable diagramId: UUID) {
        diagramEditLockService.forceRelease(diagramId)
    }

    @GetMapping
    fun listLocks(@RequestParam(required = false) modelId: UUID?): List<DiagramLockStatusResponse> =
        diagramEditLockService.listLocks(modelId)

    @PostMapping("/{diagramId}/live")
    fun relayLive(@PathVariable diagramId: UUID, @RequestBody instances: JsonNode) {
        diagramCollaborationService.relayLive(diagramId, instances)
    }

    @PostMapping("/{diagramId}/pointer")
    fun relayPointer(@PathVariable diagramId: UUID, @RequestBody request: DiagramPointerRequest) {
        diagramCollaborationService.relayPointer(diagramId, request)
    }

    @PostMapping("/{diagramId}/spectate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun spectateStart(@PathVariable diagramId: UUID) {
        diagramCollaborationService.spectateStart(diagramId)
    }

    @PostMapping("/{diagramId}/spectate/ping")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun spectatePing(@PathVariable diagramId: UUID) {
        diagramCollaborationService.spectatePing(diagramId)
    }

    @DeleteMapping("/{diagramId}/spectate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun spectateLeave(@PathVariable diagramId: UUID) {
        diagramCollaborationService.spectateLeave(diagramId)
    }
}
