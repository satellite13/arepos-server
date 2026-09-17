package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.ModelValidationLocks
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelValidationLocksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelValidationLockServiceTest {

    private val modelsRepository = mock(ModelsRepository::class.java)
    private val locksRepository = mock(ModelValidationLocksRepository::class.java)
    private val usersRepository = mock(UsersRepository::class.java)
    private val accessService = mock(ResourceAccessService::class.java)

    private val service = ModelValidationLockService(
        modelsRepository = modelsRepository,
        locksRepository = locksRepository,
        usersRepository = usersRepository,
        accessService = accessService,
        ttl = java.time.Duration.ofMinutes(10)
    )

    private val modelId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val otherId = UUID.randomUUID()
    private val model = Models(id = modelId, name = "m", version = "1.0.0", owner = Users(id = adminId, email = "owner@test.com"), createdAt = Instant.now())
    private val admin = Users(id = adminId, email = "admin@test.com").apply { role = ru.kavader.arepos.model.Role.admin }
    private val otherAdmin = Users(id = UUID.randomUUID(), email = "other@test.com").apply { role = ru.kavader.arepos.model.Role.admin }

    private fun loginAs(userId: UUID, role: String = "admin") {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            userId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))
        )
    }

    @AfterEach
    fun clearSecurityContext() {
        // SecurityContextHolder — ThreadLocal: без очистки UUID пользователя утекает
        // в следующий тест на том же потоке (AuditInterceptor пишет его в audit_log).
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `acquire succeeds when the model is unlocked`() {
        loginAs(adminId)
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))
        `when`(usersRepository.findById(adminId)).thenReturn(Optional.of(admin))
        `when`(locksRepository.findByModelId(modelId)).thenReturn(null)

        val status = service.acquire(modelId)

        assertEquals(true, status.locked)
        assertEquals(adminId, status.lockedBy)
        verify(locksRepository).save(org.mockito.ArgumentMatchers.any(ModelValidationLocks::class.java))
    }

    @Test
    fun `acquire rejects a fresh foreign lock`() {
        loginAs(adminId)
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))
        `when`(usersRepository.findById(adminId)).thenReturn(Optional.of(admin))
        `when`(locksRepository.findByModelId(modelId)).thenReturn(
            ModelValidationLocks(modelId = modelId, lockedBy = otherAdmin, lockedAt = Instant.now())
        )

        val exception = assertFailsWith<ResponseStatusException> { service.acquire(modelId) }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
    }

    @Test
    fun `acquire takes over a stale foreign lock`() {
        loginAs(adminId)
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))
        `when`(usersRepository.findById(adminId)).thenReturn(Optional.of(admin))
        `when`(locksRepository.findByModelId(modelId)).thenReturn(
            ModelValidationLocks(modelId = modelId, lockedBy = otherAdmin, lockedAt = Instant.now().minusSeconds(3600))
        )

        val status = service.acquire(modelId)

        assertEquals(adminId, status.lockedBy)
    }

    @Test
    fun `acquire is forbidden for non-admins`() {
        loginAs(adminId, role = "reader")
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))

        val exception = assertFailsWith<ResponseStatusException> { service.acquire(modelId) }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }

    @Test
    fun `release removes only the current user's lock`() {
        loginAs(adminId)
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))

        service.release(modelId)

        verify(locksRepository).deleteByModelIdAndLockedBy_Id(modelId, adminId)
    }
}
