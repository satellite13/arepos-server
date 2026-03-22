package ru.kavader.arepos.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.DiagramLockStatusResponse
import ru.kavader.arepos.service.DiagramEditLockConflictException
import ru.kavader.arepos.service.DiagramEditLockService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/diagram-locks")
class DiagramEditLocksController(
    private val diagramEditLockService: DiagramEditLockService
) {

    @PostMapping("/{diagramId}/acquire")
    fun acquire(@PathVariable diagramId: UUID): ResponseEntity<DiagramLockStatusResponse> {
        return try {
            ResponseEntity.ok(diagramEditLockService.acquire(diagramId))
        } catch (ex: DiagramEditLockConflictException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(ex.body)
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
}
