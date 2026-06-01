package ru.kavader.arepos.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramLockStatusResponse
import ru.kavader.arepos.model.DiagramEditLocks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramEditLocksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.metrics.CustomMetricsService
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class DiagramEditLockService(
    private val diagramsRepository: DiagramsRepository,
    private val modelsRepository: ModelsRepository,
    private val locksRepository: DiagramEditLocksRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val metrics: CustomMetricsService
) {

    companion object {
        val LOCK_TTL: Duration = Duration.ofSeconds(180)
        const val REASON_LOCKED_BY_OTHER = "LOCKED_BY_OTHER"
    }

    @Transactional
    fun acquire(diagramId: UUID): DiagramLockStatusResponse {
        val diagram = loadDiagram(diagramId)
        accessService.requireCanEditDiagram(diagram)
        val meId = accessService.currentUserId()
        val me = usersRepository.findById(meId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found")
        }
        val now = Instant.now()
        val newExpiry = now.plus(LOCK_TTL)

        val row = locksRepository.lockByDiagramIdForUpdate(diagramId)
        if (row == null) {
            locksRepository.save(
                DiagramEditLocks(
                    diagram = diagram,
                    lockedBy = me,
                    lockedAt = now,
                    lastHeartbeatAt = now,
                    expiresAt = newExpiry
                )
            )
            metrics.editLockAcquire.increment()
            return toHeldResponse(diagram, me, newExpiry, null)
        }
        if (row.expiresAt.isBefore(now)) {
            row.lockedBy = me
            row.lockedAt = now
            row.lastHeartbeatAt = now
            row.expiresAt = newExpiry
            metrics.editLockAcquire.increment()
            return toHeldResponse(diagram, me, newExpiry, null)
        }
        if (row.lockedBy.id == meId) {
            row.lastHeartbeatAt = now
            row.expiresAt = newExpiry
            metrics.editLockAcquire.increment()
            return toHeldResponse(diagram, me, newExpiry, null)
        }
        throw DiagramEditLockConflictException(toConflictResponse(diagram, row))
    }

    @Transactional
    fun heartbeat(diagramId: UUID): DiagramLockStatusResponse {
        val diagram = loadDiagram(diagramId)
        accessService.requireCanEditDiagram(diagram)
        val meId = accessService.currentUserId()
        val now = Instant.now()
        val newExpiry = now.plus(LOCK_TTL)

        val row = locksRepository.lockByDiagramIdForUpdate(diagramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No active lock for diagram $diagramId")
        if (row.expiresAt.isBefore(now)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lock expired for diagram $diagramId")
        }
        if (row.lockedBy.id != meId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Lock is held by another user")
        }
        row.lastHeartbeatAt = now
        row.expiresAt = newExpiry
        return toHeldResponse(diagram, row.lockedBy, newExpiry, null)
    }

    @Transactional
    fun release(diagramId: UUID) {
        val diagram = loadDiagram(diagramId)
        accessService.requireCanEditDiagram(diagram)
        val meId = accessService.currentUserId()
        val now = Instant.now()

        val row = locksRepository.lockByDiagramIdForUpdate(diagramId) ?: return
        if (row.expiresAt.isBefore(now)) {
            locksRepository.delete(row)
            return
        }
        if (row.lockedBy.id != meId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Lock is held by another user")
        }
        metrics.editLockRelease.increment()
        locksRepository.delete(row)
    }

    @Transactional
    fun forceRelease(diagramId: UUID) {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")
        }
        loadDiagram(diagramId)
        locksRepository.deleteByDiagramId(diagramId)
    }

    @Transactional(readOnly = true)
    fun listLocks(modelId: UUID?): List<DiagramLockStatusResponse> {
        val now = Instant.now()
        val rows = when {
            modelId != null -> {
                val model = modelsRepository.findById(modelId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
                }
                accessService.requireCanViewModel(model)
                locksRepository.findActiveByModelId(modelId, now)
            }
            accessService.canViewAdminPanel() -> locksRepository.findAllActive(now)
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "modelId is required")
        }
        return rows.map { row ->
            val diagram = row.diagram
            toHeldResponse(diagram, row.lockedBy, row.expiresAt, null)
        }
    }

    @Transactional
    fun deleteExpiredLocks(): Int {
        val cutoff = Instant.now()
        return locksRepository.deleteExpiredBefore(cutoff)
    }

    private fun loadDiagram(diagramId: UUID): Diagrams =
        diagramsRepository.findById(diagramId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Diagram $diagramId not found")
        }

    private fun diagramUpdatedAt(diagram: Diagrams): Instant =
        diagram.updatedAt ?: diagram.createdAt ?: Instant.EPOCH

    private fun userDisplay(user: Users): String = user.email

    private fun toHeldResponse(
        diagram: Diagrams,
        holder: Users,
        expiresAt: Instant,
        reason: String?
    ): DiagramLockStatusResponse {
        val diagramId = diagram.id!!
        val holderId = holder.id!!
        return DiagramLockStatusResponse(
            diagramId = diagramId,
            isLocked = true,
            lockedByUserId = holderId,
            lockedByDisplay = userDisplay(holder),
            expiresAt = expiresAt,
            diagramUpdatedAt = diagramUpdatedAt(diagram),
            reason = reason
        )
    }

    private fun toConflictResponse(diagram: Diagrams, row: DiagramEditLocks): DiagramLockStatusResponse =
        toHeldResponse(diagram, row.lockedBy, row.expiresAt, REASON_LOCKED_BY_OTHER)
}
