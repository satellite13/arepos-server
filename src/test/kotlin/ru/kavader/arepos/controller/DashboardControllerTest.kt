package ru.kavader.arepos.controller

import org.hamcrest.Matchers.hasItems
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.mockito.Mockito.doAnswer
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.AuditLog
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.AuditLogRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var auditLogRepository: AuditLogRepository

    @MockitoSpyBean
    lateinit var accessService: ResourceAccessService

    @BeforeEach
    fun setupCerbosMock() {
        doAnswer { CurrentUser.getRole() == "ADMIN" }
            .`when`(accessService)
            .canViewAdminPanel()
    }

    @Test
    fun `recent activity returns only current user entries for non-admin`() {
        val currentUser = usersRepository.save(
            Users(
                email = "dashboard-user@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val anotherUser = usersRepository.save(
            Users(
                email = "dashboard-other@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        auditLogRepository.saveAll(
            listOf(
                AuditLog(
                    tableName = "models",
                    operation = "UPDATE",
                    rowId = UUID.randomUUID(),
                    changedBy = currentUser,
                    changedAt = Instant.now().minusSeconds(10)
                ),
                AuditLog(
                    tableName = "models",
                    operation = "DELETE",
                    rowId = UUID.randomUUID(),
                    changedBy = anotherUser,
                    changedAt = Instant.now().minusSeconds(5)
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/dashboard/recent?limit=10")
                .withAuth(currentUser.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activity.length()").value(1))
            .andExpect(jsonPath("$.activity[0].changedById").value(currentUser.id.toString()))
    }

    @Test
    fun `recent activity returns all entries for admin`() {
        val admin = usersRepository.save(
            Users(
                email = "dashboard-admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val user = usersRepository.save(
            Users(
                email = "dashboard-user2@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        auditLogRepository.saveAll(
            listOf(
                AuditLog(
                    tableName = "models",
                    operation = "UPDATE",
                    rowId = UUID.randomUUID(),
                    changedBy = admin,
                    changedAt = Instant.now().minusSeconds(20)
                ),
                AuditLog(
                    tableName = "notations",
                    operation = "INSERT",
                    rowId = UUID.randomUUID(),
                    changedBy = user,
                    changedAt = Instant.now().minusSeconds(10)
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/dashboard/recent?limit=10")
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$.activity[*].changedById").value(
                    hasItems(admin.id.toString(), user.id.toString())
                )
            )
    }
}
