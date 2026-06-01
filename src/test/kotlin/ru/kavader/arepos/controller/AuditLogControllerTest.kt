package ru.kavader.arepos.controller
import ru.kavader.arepos.dto.auth.*
import ru.kavader.arepos.dto.system.*

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.mockito.Mockito.doAnswer
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @MockitoSpyBean
    lateinit var accessService: ResourceAccessService

    @BeforeEach
    fun setupCerbosMock() {
        doAnswer { CurrentUser.getRole() == "ADMIN" }
            .`when`(accessService)
            .canViewAdminPanel()
    }

    @Test
    fun `lists audit log entries`() {
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        RegisterRequest(
                            email = "audit@test.com",
                            password = "password123",
                            firstName = "Аудит",
                            lastName = "Тестов",
                            middleName = null,
                            position = "Инженер"
                        )
                    )
                )
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/audit-log?page=0&size=5")
                .withAuth(admin.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(greaterThan(0)))
    }
}
