package ru.kavader.arepos.featuregrant

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.RoleFeatureGrants
import ru.kavader.arepos.model.UserFeatureGrantAllows
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.RoleFeatureGrantsRepository
import ru.kavader.arepos.repository.UserFeatureGrantAllowsRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class FeatureGrantServiceTest {

    @Mock
    lateinit var roleRepo: RoleFeatureGrantsRepository

    @Mock
    lateinit var userAllowRepo: UserFeatureGrantAllowsRepository

    private lateinit var service: FeatureGrantService

    @BeforeEach
    fun setUp() {
        service = FeatureGrantService(roleRepo, userAllowRepo)
    }

    @Test
    fun `admin always gets full catalog even with empty DB rows`() {
        val user = Users(
            id = UUID.randomUUID(),
            email = "admin@test.com",
            role = Role.admin,
        )

        val grants = service.effectiveGrants(user)

        assertEquals(FeatureGrantKeys.ALL, grants)
        verifyNoInteractions(roleRepo, userAllowRepo)
    }

    @Test
    fun `editor gets role defaults union allows`() {
        val userId = UUID.randomUUID()
        val user = Users(
            id = userId,
            email = "editor@test.com",
            role = Role.editor,
        )
        `when`(roleRepo.findByRole(Role.editor.name)).thenReturn(
            listOf(RoleFeatureGrants(role = Role.editor.name, grantKey = "model.export"))
        )
        `when`(userAllowRepo.findByUserId(userId)).thenReturn(
            listOf(UserFeatureGrantAllows(userId = userId, grantKey = "model.inspectJson"))
        )

        val grants = service.effectiveGrants(user)

        assertTrue(grants.containsAll(listOf("model.export", "model.inspectJson")))
        assertEquals(listOf("model.export", "model.inspectJson"), grants)
    }

    @Test
    fun `replaceRoleGrants rejects admin with 400`() {
        val ex = assertThrows<ResponseStatusException> {
            service.replaceRoleGrants(Role.admin, listOf("model.export"))
        }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(roleRepo, never()).deleteByRole(anyString())
        verifyNoInteractions(userAllowRepo)
    }

    @Test
    fun `unknown grant keys rejected on replaceRoleGrants`() {
        val ex = assertThrows<ResponseStatusException> {
            service.replaceRoleGrants(Role.editor, listOf("nope"))
        }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(roleRepo, never()).deleteByRole(anyString())
        verify(roleRepo, never()).saveAll(anyList())
    }

    @Test
    fun `unknown grant keys rejected on replaceUserAllows`() {
        val userId = UUID.randomUUID()

        val ex = assertThrows<ResponseStatusException> {
            service.replaceUserAllows(userId, listOf("model.export", "nope"))
        }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(userAllowRepo, never()).deleteByUserId(userId)
        verify(userAllowRepo, never()).saveAll(anyList())
    }

    @Test
    fun `replaceRoleGrants full replaces known keys`() {
        `when`(roleRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        service.replaceRoleGrants(Role.editor, listOf("model.export", "notation.nav", "model.export"))

        verify(roleRepo).deleteByRole(Role.editor.name)
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<RoleFeatureGrants>>
        verify(roleRepo).saveAll(captor.capture())
        assertEquals(
            listOf("model.export", "notation.nav"),
            captor.value.map { it.grantKey }.sorted()
        )
        assertTrue(captor.value.all { it.role == Role.editor.name })
    }

    @Test
    fun `roleGrantsMatrix returns non-admin roles from DB`() {
        `when`(roleRepo.findAll()).thenReturn(
            listOf(
                RoleFeatureGrants(role = Role.editor.name, grantKey = "model.export"),
                RoleFeatureGrants(role = Role.editor.name, grantKey = "notation.nav"),
                RoleFeatureGrants(role = Role.viewer.name, grantKey = "model.relationMatrix"),
                RoleFeatureGrants(role = Role.admin.name, grantKey = "model.create"),
            )
        )

        val matrix = service.roleGrantsMatrix()

        assertEquals(
            mapOf(
                "editor" to listOf("model.export", "notation.nav"),
                "viewer" to listOf("model.relationMatrix"),
            ),
            matrix
        )
    }

    @Test
    fun `userAllows returns sorted distinct keys`() {
        val userId = UUID.randomUUID()
        `when`(userAllowRepo.findByUserId(userId)).thenReturn(
            listOf(
                UserFeatureGrantAllows(userId = userId, grantKey = "notation.nav"),
                UserFeatureGrantAllows(userId = userId, grantKey = "model.export"),
            )
        )

        assertEquals(listOf("model.export", "notation.nav"), service.userAllows(userId))
    }
}
