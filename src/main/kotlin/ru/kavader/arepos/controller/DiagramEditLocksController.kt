package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.kavader.arepos.dto.common.ListResponse
import ru.kavader.arepos.dto.common.toListResponse
import ru.kavader.arepos.dto.model.DiagramLockStatusResponse
import ru.kavader.arepos.dto.model.DiagramPointerRequest
import ru.kavader.arepos.service.DiagramCollaborationService
import ru.kavader.arepos.service.DiagramEditLockConflictException
import ru.kavader.arepos.service.DiagramEditLockService
import java.util.*

@RestController
@RequestMapping("/api/v1/diagram-locks")
@Tag(name = "Diagram Locks", description = "Diagram lock and collaboration endpoints")
class DiagramEditLocksController(
    private val diagramEditLockService: DiagramEditLockService,
    private val diagramCollaborationService: DiagramCollaborationService
) {

    @PostMapping("/{diagramId}/acquire")
    @Operation(summary = "Acquire diagram edit lock")
    fun acquire(@PathVariable diagramId: UUID): DiagramLockStatusResponse {
        return try {
            diagramEditLockService.acquire(diagramId)
        } catch (ex: DiagramEditLockConflictException) {
            // Always 200: clients distinguish hold vs conflict via reason=LOCKED_BY_OTHER.
            ex.body
        }
    }

    @PostMapping("/{diagramId}/heartbeat")
    @Operation(summary = "Send lock heartbeat")
    fun heartbeat(@PathVariable diagramId: UUID): DiagramLockStatusResponse =
        diagramEditLockService.heartbeat(diagramId)

    @PostMapping("/{diagramId}/release")
    @Operation(summary = "Release own diagram lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun release(@PathVariable diagramId: UUID) {
        diagramEditLockService.release(diagramId)
    }

    @PostMapping("/{diagramId}/force-release")
    @Operation(summary = "Force release diagram lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forceRelease(@PathVariable diagramId: UUID) {
        diagramEditLockService.forceRelease(diagramId)
    }

    @GetMapping
    @Operation(summary = "List active diagram locks")
    fun listLocks(@RequestParam(required = false) modelId: UUID?): ListResponse<DiagramLockStatusResponse> =
        diagramEditLockService.listLocks(modelId).toListResponse()

    @PostMapping("/{diagramId}/live")
    @Operation(summary = "Relay live canvas instances")
    fun relayLive(@PathVariable diagramId: UUID, @RequestBody instances: JsonNode) {
        diagramCollaborationService.relayLive(diagramId, instances)
    }

    @PostMapping("/{diagramId}/pointer")
    @Operation(summary = "Relay collaborator pointer")
    fun relayPointer(@PathVariable diagramId: UUID, @RequestBody @Valid request: DiagramPointerRequest) {
        diagramCollaborationService.relayPointer(diagramId, request)
    }

    @PostMapping("/{diagramId}/spectate")
    @Operation(summary = "Start diagram spectating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun spectateStart(@PathVariable diagramId: UUID) {
        diagramCollaborationService.spectateStart(diagramId)
    }

    @PostMapping("/{diagramId}/spectate/ping")
    @Operation(summary = "Ping diagram spectating session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun spectatePing(@PathVariable diagramId: UUID) {
        diagramCollaborationService.spectatePing(diagramId)
    }

    @DeleteMapping("/{diagramId}/spectate")
    @Operation(summary = "Stop diagram spectating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun spectateLeave(@PathVariable diagramId: UUID) {
        diagramCollaborationService.spectateLeave(diagramId)
    }
}
