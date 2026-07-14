package ru.kavader.arepos.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import ru.kavader.arepos.metrics.CustomMetricsService
import ru.kavader.arepos.model.DiagramEditLocks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramEditLocksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class DiagramEditLockServiceTest {

    @Mock
    lateinit var diagramsRepository: DiagramsRepository

    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var locksRepository: DiagramEditLocksRepository

    @Mock
    lateinit var usersRepository: UsersRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    @Test
    fun `acquire and heartbeat use configured ttl`() {
        val user = Users(id = UUID.randomUUID(), email = "lock-owner@test.com")
        val model = Models(
            id = UUID.randomUUID(),
            name = "lock-model",
            version = "1.0.0",
            owner = user
        )
        val notation = Notations(
            id = UUID.randomUUID(),
            name = "lock-notation",
            version = "1.0.0",
            owner = user
        )
        val diagram = Diagrams(
            id = UUID.randomUUID(),
            name = "lock-diagram",
            version = "1.0.0",
            owner = user,
            model = model,
            notation = notation,
            createdAt = Instant.now()
        )
        val lockRef = AtomicReference<DiagramEditLocks?>()
        `when`(diagramsRepository.findById(diagram.id!!)).thenReturn(Optional.of(diagram))
        `when`(accessService.currentUserId()).thenReturn(user.id)
        `when`(usersRepository.findById(user.id!!)).thenReturn(Optional.of(user))
        `when`(locksRepository.lockByDiagramIdForUpdate(diagram.id!!))
            .thenAnswer { lockRef.get() }
        `when`(locksRepository.saveAndFlush(any(DiagramEditLocks::class.java)))
            .thenAnswer { invocation ->
                invocation.getArgument<DiagramEditLocks>(0).also(lockRef::set)
            }
        val service = DiagramEditLockService(
            diagramsRepository,
            modelsRepository,
            locksRepository,
            usersRepository,
            accessService,
            CustomMetricsService(SimpleMeterRegistry()),
            lockTtlSeconds = 7
        )

        val acquired = service.acquire(diagram.id!!)
        val acquiredLock = requireNotNull(lockRef.get())
        assertTrue(acquired.isLocked)
        assertEquals(user.id, acquired.lockedByUserId)
        assertEquals(
            Duration.ofSeconds(7),
            Duration.between(acquiredLock.lastHeartbeatAt, acquiredLock.expiresAt)
        )

        val renewed = service.heartbeat(diagram.id!!)
        assertTrue(renewed.isLocked)
        assertEquals(
            Duration.ofSeconds(7),
            Duration.between(acquiredLock.lastHeartbeatAt, acquiredLock.expiresAt)
        )
        assertEquals(acquiredLock.expiresAt, renewed.expiresAt)
    }
}
