package ru.kavader.arepos.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.UserDiagramFavorite
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UserDiagramFavoriteRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class DiagramFavoritesControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Autowired
    lateinit var userDiagramFavoriteRepository: UserDiagramFavoriteRepository

    @MockitoSpyBean
    lateinit var accessService: ResourceAccessService

    @BeforeEach
    fun setupCerbosMock() {
        doAnswer { CurrentUser.getRole() == "ADMIN" }
            .`when`(accessService)
            .canViewAdminPanel()
    }

    @Test
    fun `put favorite toggles idempotently and lists ids`() {
        val owner = persistUser("fav-owner@test.com", Role.ADMIN)
        val diagram = persistDiagramFor(owner)

        mockMvc.perform(put("/api/v1/diagrams/${diagram.id}/favorite").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isNoContent)
        mockMvc.perform(put("/api/v1/diagrams/${diagram.id}/favorite").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isNoContent)

        assertEquals(1, userDiagramFavoriteRepository.countByDiagramId(diagram.id!!))

        mockMvc.perform(get("/api/v1/users/me/favorite-diagram-ids").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ids.length()").value(1))
            .andExpect(jsonPath("$.ids[0]").value(diagram.id.toString()))
    }

    @Test
    fun `delete favorite removes row and stays idempotent`() {
        val owner = persistUser("fav-del@test.com", Role.ADMIN)
        val diagram = persistDiagramFor(owner)
        persistFavoriteRow(owner, diagram)

        mockMvc.perform(delete("/api/v1/diagrams/${diagram.id}/favorite").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isNoContent)
        mockMvc.perform(delete("/api/v1/diagrams/${diagram.id}/favorite").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/users/me/favorite-diagram-ids").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ids.length()").value(0))
    }

    @Test
    fun `add favorite returns 404 for missing diagram`() {
        val owner = persistUser("fav-404@test.com", Role.ADMIN)
        mockMvc.perform(put("/api/v1/diagrams/${UUID.randomUUID()}/favorite").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `favorite endpoints require authentication`() {
        mockMvc.perform(put("/api/v1/diagrams/${UUID.randomUUID()}/favorite"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/users/me/favorite-diagrams"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/users/me/favorite-diagram-ids"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `favorite-diagrams hides inaccessible diagrams for non-admin`() {
        val owner = persistUser("fav-hidden-owner@test.com", Role.USER)
        val viewer = persistUser("fav-hidden-viewer@test.com", Role.USER)
        val diagram = persistDiagramFor(owner)
        persistFavoriteRow(viewer, diagram)

        mockMvc.perform(get("/api/v1/users/me/favorite-diagrams?size=10").withAuth(viewer.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))

        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = diagram.model.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(get("/api/v1/users/me/favorite-diagrams?size=10").withAuth(viewer.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(diagram.id.toString()))
            .andExpect(jsonPath("$.page.totalElements").value(1))
    }

    @Test
    fun `favorite-diagrams returns all favorites for admin`() {
        val owner = persistUser("fav-admin-owner@test.com", Role.USER)
        val admin = persistUser("fav-admin@test.com", Role.ADMIN)
        val diagram = persistDiagramFor(owner)
        persistFavoriteRow(admin, diagram)

        mockMvc.perform(get("/api/v1/users/me/favorite-diagrams?size=10").withAuth(admin.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(diagram.id.toString()))
    }

    @Test
    fun `favorite-diagrams paginates with most-recent-first ordering`() {
        val owner = persistUser("fav-order@test.com", Role.ADMIN)
        val first = persistDiagramFor(owner)
        val second = persistDiagramFor(owner)
        val third = persistDiagramFor(owner)
        val base = Instant.now().minusSeconds(120)
        persistFavoriteRow(owner, first, base)
        persistFavoriteRow(owner, second, base.plusSeconds(60))
        persistFavoriteRow(owner, third, base.plusSeconds(120))

        mockMvc.perform(get("/api/v1/users/me/favorite-diagrams?page=0&size=2").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").value(third.id.toString()))
            .andExpect(jsonPath("$.content[1].id").value(second.id.toString()))
            .andExpect(jsonPath("$.page.totalElements").value(3))

        mockMvc.perform(get("/api/v1/users/me/favorite-diagrams?page=1&size=2").withAuth(owner.id!!, Role.ADMIN))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(first.id.toString()))
    }

    private fun persistUser(email: String, role: Role): Users = usersRepository.save(
        Users(
            email = email,
            role = role,
            createdAt = Instant.now()
        )
    )

    private fun persistDiagramFor(owner: Users): Diagrams {
        val model = modelsRepository.save(
            Models(
                name = "fav-model-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        val notation = notationsRepository.save(
            Notations(
                name = "fav-notation-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        return diagramsRepository.save(
            Diagrams(
                name = "fav-diagram-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                createdAt = Instant.now()
            )
        )
    }

    private fun persistFavoriteRow(user: Users, diagram: Diagrams, createdAt: Instant = Instant.now()) =
        userDiagramFavoriteRepository.save(
            UserDiagramFavorite(user = user, diagram = diagram, createdAt = createdAt)
        )
}
