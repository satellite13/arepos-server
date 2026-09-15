package ru.kavader.arepos.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.AutoMergeLockStatus
import ru.kavader.arepos.model.ModelValidationLocks
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelValidationLocksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Model-scoped lock for the validation auto-merge: only one run per model at a time.
 * Acquiring requires the ADMIN role and model edit permission; a foreign lock held
 * longer than [ttl] is considered stale and may be taken over.
 */
@Service
class ModelValidationLockService(
    private val modelsRepository: ModelsRepository,
    private val locksRepository: ModelValidationLocksRepository,
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService,
    @Value("\${arepos.validation-auto-merge.lock-ttl:PT10M}")
    private val ttl: Duration,
) {

    @Transactional(readOnly = true)
    fun status(modelId: UUID): AutoMergeLockStatus {
        val model = requireModel(modelId)
        accessService.requireCanViewModel(model)
        val lock = locksRepository.findByModelId(modelId)
        return toStatus(lock)
    }

    @Transactional
    fun acquire(modelId: UUID): AutoMergeLockStatus {
        val model = requireModel(modelId)
        requireAdmin()
        accessService.requireCanEditModel(model)
        val userId = CurrentUser.getId()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
        val user = usersRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")
        }

        val existing = locksRepository.findByModelId(modelId)
        if (existing != null && existing.lockedBy.id != userId && !isStale(existing.lockedAt)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Auto-merge is already running for this model"
            )
        }

        val lock = existing ?: ModelValidationLocks(modelId = modelId, lockedBy = user)
        lock.lockedBy = user
        lock.lockedAt = Instant.now()
        locksRepository.save(lock)
        return toStatus(lock)
    }

    @Transactional
    fun release(modelId: UUID) {
        val model = requireModel(modelId)
        requireAdmin()
        accessService.requireCanEditModel(model)
        val userId = CurrentUser.getId() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        locksRepository.deleteByModelIdAndLockedBy_Id(modelId, userId)
    }

    private fun toStatus(lock: ModelValidationLocks?): AutoMergeLockStatus {
        if (lock == null) return AutoMergeLockStatus(locked = false)
        val active = !isStale(lock.lockedAt)
        if (!active) return AutoMergeLockStatus(locked = false)
        val holder = lock.lockedBy
        return AutoMergeLockStatus(
            locked = true,
            lockedBy = lock.lockedBy.id,
            lockedByName = holder.email,
            lockedAt = lock.lockedAt
        )
    }

    private fun isStale(lockedAt: Instant?): Boolean =
        lockedAt == null || Duration.between(lockedAt, Instant.now()) > ttl

    private fun requireAdmin() {
        if (!CurrentUser.getRole().equals("ADMIN", ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role required")
        }
    }

    private fun requireModel(modelId: UUID): Models =
        modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
}
