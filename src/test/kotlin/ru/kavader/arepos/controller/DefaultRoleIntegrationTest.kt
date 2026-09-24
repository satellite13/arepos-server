package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.auth.RegisterRequest
import ru.kavader.arepos.dto.user.UserRequest
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = ["arepos.auth.default-role=architect"]
)
class DefaultRoleIntegrationTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @MockitoSpyBean
    lateinit var accessService: ResourceAccessService

    @BeforeEach
    fun setupAuthzStubs() {
        doAnswer { CurrentUser.getRole() == "admin" }
            .`when`(accessService)
            .canManageUsers()
    }

    @Test
    fun `registers new user with configured default role`() {
        val payload = RegisterRequest(
            email = "arch-default@test.com",
            password = "ValidPass1",
            firstName = "Иван",
            lastName = "Иванов"
        )

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user.email").value("arch-default@test.com"))
            .andExpect(jsonPath("$.user.role").value("architect"))
    }

    @Test
    fun `admin-created user falls back to configured default role`() {
        val admin = usersRepository.save(
            Users(
                email = "admin@test.com",
                role = Role.admin,
                createdAt = Instant.now()
            )
        )

        val payload = UserRequest(
            email = "no-role@test.com",
            attrs = null
        )

        mockMvc.perform(
            post("/api/v1/users")
                .withAuth(admin.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.role").value("architect"))
    }
}
