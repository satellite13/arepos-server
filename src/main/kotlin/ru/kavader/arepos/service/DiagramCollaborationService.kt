package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.DiagramPointerRequest
import ru.kavader.arepos.dto.DiagramSpectatorView
import ru.kavader.arepos.model.DiagramEditLocks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.repository.DiagramEditLocksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class DiagramCollaborationService(
    private val diagramsRepository: DiagramsRepository,
    private val locksRepository: DiagramEditLocksRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val modelSyncBroadcaster: ModelSyncBroadcaster,
    private val objectMapper: ObjectMapper
) {

    companion object {
        val MAX_LIVE_PAYLOAD_BYTES: Int = 512 * 1024
        val SPECTATOR_TTL: Duration = Duration.ofSeconds(45)
    }

    private data class SpectatorEntry(
        val displayName: String,
        @Volatile var lastSeen: Instant
    )

    private val spectatorsByDiagram = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, SpectatorEntry>>()

    @Transactional(readOnly = true)
    fun relayLive(diagramId: UUID, instances: JsonNode) {
        val diagram = assertLockHolder(diagramId)
        val bytes = objectMapper.writeValueAsBytes(instances)
        if (bytes.size > MAX_LIVE_PAYLOAD_BYTES) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "instances payload too large")
        }
        val modelId = diagram.model.id!!
        modelSyncBroadcaster.broadcastDiagramLive(modelId, diagramId, instances)
    }

    @Transactional(readOnly = true)
    fun relayPointer(diagramId: UUID, request: DiagramPointerRequest) {
        val diagram = assertLockHolder(diagramId)
        val modelId = diagram.model.id!!
        modelSyncBroadcaster.broadcastDiagramPointer(
            modelId,
            diagramId,
            request.worldX,
            request.worldY,
            request.visible
        )
    }

    fun spectateStart(diagramId: UUID) {
        val diagram = loadDiagram(diagramId)
        accessService.requireCanViewDiagram(diagram)
        val meId = accessService.currentUserId()
        val me = usersRepository.findById(meId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")
        }
        val now = Instant.now()
        val row = locksRepository.findActiveWithDiagram(diagramId, now)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Diagram is not locked")
        if (row.lockedBy.id == meId) {
            return
        }
        val display = me.email
        val inner = spectatorsByDiagram.computeIfAbsent(diagramId) { ConcurrentHashMap() }
        inner[meId] = SpectatorEntry(display, now)
        broadcastSpectators(diagram, diagramId)
    }

    fun spectatePing(diagramId: UUID) {
        val diagram = loadDiagram(diagramId)
        accessService.requireCanViewDiagram(diagram)
        val meId = accessService.currentUserId()
        val now = Instant.now()
        val row = locksRepository.findActiveWithDiagram(diagramId, now) ?: run {
            spectateLeave(diagramId)
            return
        }
        if (row.lockedBy.id == meId) {
            return
        }
        val inner = spectatorsByDiagram[diagramId] ?: run {
            spectateStart(diagramId)
            return
        }
        val entry = inner[meId] ?: run {
            spectateStart(diagramId)
            return
        }
        entry.lastSeen = now
        broadcastSpectators(diagram, diagramId)
    }

    fun spectateLeave(diagramId: UUID) {
        val diagram = diagramsRepository.findById(diagramId).orElse(null) ?: return
        accessService.requireCanViewDiagram(diagram)
        val meId = accessService.currentUserId()
        val inner = spectatorsByDiagram[diagramId] ?: return
        if (inner.remove(meId) != null && inner.isEmpty()) {
            spectatorsByDiagram.remove(diagramId)
        }
        broadcastSpectators(diagram, diagramId)
    }

    /** Removes stale spectator entries system-wide (scheduled). */
    fun purgeStaleSpectators() {
        val cutoff = Instant.now().minus(SPECTATOR_TTL)
        for (diagramId in spectatorsByDiagram.keys.toList()) {
            val inner = spectatorsByDiagram[diagramId] ?: continue
            val toRemove = inner.filter { it.value.lastSeen.isBefore(cutoff) }.keys
            var changed = false
            for (uid in toRemove) {
                if (inner.remove(uid) != null) {
                    changed = true
                }
            }
            if (inner.isEmpty()) {
                spectatorsByDiagram.remove(diagramId)
            }
            if (changed) {
                val diagram = diagramsRepository.findById(diagramId).orElse(null) ?: continue
                broadcastSpectators(diagram, diagramId)
            }
        }
    }

    private fun broadcastSpectators(diagram: Diagrams, diagramId: UUID) {
        val modelId = diagram.model.id!!
        val inner = spectatorsByDiagram[diagramId]
        val viewers =
            inner?.map { (uid, e) -> DiagramSpectatorView(uid, e.displayName) }
                ?.sortedBy { it.displayName.lowercase() }
                ?: emptyList()
        modelSyncBroadcaster.broadcastDiagramSpectators(modelId, diagramId, viewers)
    }

    private fun loadDiagram(diagramId: UUID): Diagrams =
        diagramsRepository.findById(diagramId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $diagramId not found")
        }

    private fun assertLockHolder(diagramId: UUID): Diagrams {
        val diagram = loadDiagram(diagramId)
        accessService.requireCanEditDiagram(diagram)
        val meId = accessService.currentUserId()
        val now = Instant.now()
        val row: DiagramEditLocks = locksRepository.findActiveWithDiagram(diagramId, now)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "No active lock for diagram")
        if (row.lockedBy.id != meId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Lock is held by another user")
        }
        return diagram
    }
}
