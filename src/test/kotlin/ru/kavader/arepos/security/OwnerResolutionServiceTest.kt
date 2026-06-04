package ru.kavader.arepos.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class OwnerResolutionServiceTest {
    @Mock
    lateinit var usersRepository: UsersRepository

    @Mock
    lateinit var accessService: ResourceAccessService

    private fun service(): OwnerResolutionService = OwnerResolutionService(usersRepository, accessService)

    private fun authAs(userId: UUID, role: Role = Role.USER) {
        val auth = UsernamePasswordAuthenticationToken(
            userId,
            "n/a",
            listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    @AfterEach
    fun cleanupSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `resolveOwnerForCreate uses requested owner for admin`() {
        val currentUserId = UUID.randomUUID()
        val requestedOwnerId = UUID.randomUUID()
        authAs(currentUserId, Role.ADMIN)

        val requestedOwner = Users(id = requestedOwnerId, email = "owner@test.com", createdAt = Instant.now())
        `when`(accessService.canViewAdminPanel()).thenReturn(true)
        `when`(usersRepository.findById(requestedOwnerId)).thenReturn(Optional.of(requestedOwner))

        val resolved = service().resolveOwnerForCreate(requestedOwnerId)

        assertEquals(requestedOwnerId, resolved.id)
        verify(usersRepository).findById(requestedOwnerId)
    }

    @Test
    fun `resolveOwnerForCreate ignores requested owner for non-admin`() {
        val currentUserId = UUID.randomUUID()
        val requestedOwnerId = UUID.randomUUID()
        authAs(currentUserId, Role.USER)

        val currentUser = Users(id = currentUserId, email = "self@test.com", createdAt = Instant.now())
        `when`(accessService.canViewAdminPanel()).thenReturn(false)
        `when`(usersRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser))

        val resolved = service().resolveOwnerForCreate(requestedOwnerId)

        assertEquals(currentUserId, resolved.id)
        verify(usersRepository).findById(currentUserId)
        verify(usersRepository, never()).findById(requestedOwnerId)
    }

    @Test
    fun `resolveOwnerForUpdate reassigns owner only for admin`() {
        val currentUserId = UUID.randomUUID()
        val requestedOwnerId = UUID.randomUUID()
        authAs(currentUserId, Role.ADMIN)

        val currentOwner = Users(id = UUID.randomUUID(), email = "old@test.com", createdAt = Instant.now())
        val requestedOwner = Users(id = requestedOwnerId, email = "new@test.com", createdAt = Instant.now())
        `when`(accessService.canViewAdminPanel()).thenReturn(true)
        `when`(usersRepository.findById(requestedOwnerId)).thenReturn(Optional.of(requestedOwner))

        val resolved = service().resolveOwnerForUpdate(requestedOwnerId, currentOwner)

        assertEquals(requestedOwnerId, resolved.id)
    }

    @Test
    fun `resolveReadableOwner returns null when non-admin has shared access to foreign owner`() {
        val currentUserId = UUID.randomUUID()
        val foreignOwnerId = UUID.randomUUID()
        authAs(currentUserId, Role.USER)
        `when`(accessService.canViewAdminPanel()).thenReturn(false)

        val resolved = service().resolveReadableOwner(foreignOwnerId) { ownerId, userId ->
            ownerId == foreignOwnerId && userId == currentUserId
        }

        assertNull(resolved)
    }

    @Test
    fun `resolveReadableOwner throws forbidden when non-admin has no shared access`() {
        val currentUserId = UUID.randomUUID()
        val foreignOwnerId = UUID.randomUUID()
        authAs(currentUserId, Role.USER)
        `when`(accessService.canViewAdminPanel()).thenReturn(false)

        val ex = assertThrows<ResponseStatusException> {
            service().resolveReadableOwner(foreignOwnerId) { _, _ -> false }
        }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
    }

    @Test
    fun `resolveReadableOwner returns current user for non-admin without owner filter`() {
        val currentUserId = UUID.randomUUID()
        authAs(currentUserId, Role.USER)
        `when`(accessService.canViewAdminPanel()).thenReturn(false)
        val currentUser = Users(id = currentUserId, email = "self@test.com", createdAt = Instant.now())
        `when`(usersRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser))

        val resolved = service().resolveReadableOwner(null) { _, _ -> false }

        assertEquals(currentUserId, resolved?.id)
        verify(usersRepository).findById(currentUserId)
    }
}
