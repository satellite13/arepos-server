package ru.kavader.arepos.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DiagramLockStatusResponse
import ru.kavader.arepos.metrics.CustomMetricsService
import ru.kavader.arepos.model.DiagramEditLocks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramEditLocksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ADMIN_ONLY
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Duration
import java.time.Instant
import java.util.*

@Service
class DiagramEditLockService(
    private val diagramsRepository: DiagramsRepository,
    private val modelsRepository: ModelsRepository,
    private val locksRepository: DiagramEditLocksRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    private val metrics: CustomMetricsService,
    @Value($$"${arepos.diagram-lock.ttl-seconds:180}") lockTtlSeconds: Long = 180
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lockTtl = Duration.ofSeconds(lockTtlSeconds)

    companion object {
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
        val newExpiry = now.plus(lockTtl)

        val row = locksRepository.lockByDiagramIdForUpdate(diagramId)
        if (row == null) {
            try {
                locksRepository.saveAndFlush(
                    DiagramEditLocks(
                        diagram = diagram,
                        lockedBy = me,
                        lockedAt = now,
                        lastHeartbeatAt = now,
                        expiresAt = newExpiry
                    )
                )
            } catch (_: DataIntegrityViolationException) {
                val conflictRow = locksRepository.findActiveWithDiagram(diagramId, now)
                if (conflictRow != null) {
                    throw DiagramEditLockConflictException(toConflictResponse(diagram, conflictRow))
                }
                throw ResponseStatusException(HttpStatus.CONFLICT, "Diagram lock already exists")
            }
            metrics.editLockAcquire.increment()
            log.info("diagram lock acquired: diagramId={}, userId={}, mode=new", diagramId, meId)
            return toHeldResponse(diagram, me, newExpiry, null)
        }
        if (row.expiresAt.isBefore(now)) {
            try {
                row.lockedBy = me
                row.lockedAt = now
                row.lastHeartbeatAt = now
                row.expiresAt = newExpiry
                locksRepository.saveAndFlush(row)
            } catch (_: ObjectOptimisticLockingFailureException) {
                val conflictRow = locksRepository.findActiveWithDiagram(diagramId, now)
                if (conflictRow != null) {
                    throw DiagramEditLockConflictException(toConflictResponse(diagram, conflictRow))
                }
                throw ResponseStatusException(HttpStatus.CONFLICT, "Diagram lock changed concurrently")
            }
            metrics.editLockAcquire.increment()
            log.info("diagram lock acquired: diagramId={}, userId={}, mode=expired_retake", diagramId, meId)
            return toHeldResponse(diagram, me, newExpiry, null)
        }
        if (row.lockedBy.id == meId) {
            try {
                row.lastHeartbeatAt = now
                row.expiresAt = newExpiry
                locksRepository.saveAndFlush(row)
            } catch (_: ObjectOptimisticLockingFailureException) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Diagram lock changed concurrently")
            }
            metrics.editLockAcquire.increment()
            log.debug("diagram lock heartbeat via acquire: diagramId={}, userId={}", diagramId, meId)
            return toHeldResponse(diagram, me, newExpiry, null)
        }
        log.warn(
            "diagram lock conflict: diagramId={}, requestedBy={}, heldBy={}",
            diagramId,
            meId,
            row.lockedBy.id
        )
        throw DiagramEditLockConflictException(toConflictResponse(diagram, row))
    }

    @Transactional
    fun heartbeat(diagramId: UUID): DiagramLockStatusResponse {
        val diagram = loadDiagram(diagramId)
        accessService.requireCanEditDiagram(diagram)
        val meId = accessService.currentUserId()
        val now = Instant.now()
        val newExpiry = now.plus(lockTtl)

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
        log.debug("diagram lock heartbeat: diagramId={}, userId={}", diagramId, meId)
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
            log.info("diagram lock removed on release due to expiry: diagramId={}", diagramId)
            return
        }
        if (row.lockedBy.id != meId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Lock is held by another user")
        }
        metrics.editLockRelease.increment()
        locksRepository.delete(row)
        log.info("diagram lock released: diagramId={}, userId={}", diagramId, meId)
    }

    @Transactional
    fun forceRelease(diagramId: UUID) {
        if (!accessService.canViewAdminPanel()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, ADMIN_ONLY)
        }
        loadDiagram(diagramId)
        locksRepository.deleteByDiagramId(diagramId)
        log.warn("diagram lock force released: diagramId={}", diagramId)
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
