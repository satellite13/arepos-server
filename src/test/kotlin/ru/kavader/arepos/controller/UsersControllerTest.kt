package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class UsersControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `creates user via REST`() {
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        val payload = UserRequest(
            email = "test@example.com",
            attrs = """{"role":"admin"}"""
        )

        mockMvc.perform(
            post("/api/v1/users")
                .withAuth(admin.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.attrs").value("""{"role":"admin"}"""))

        assertEquals(2, usersRepository.count())
    }

    @Test
    fun `lists users with pagination`() {
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user1@test.com",
                createdAt = Instant.now()
            )
        )
        usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user2@test.com",
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/users?page=0&size=10")
                .withAuth(admin.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.totalElements").value(3))
    }

    @Test
    fun `filters users by email`() {
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "john@test.com",
                createdAt = Instant.now()
            )
        )
        usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "jane@test.com",
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/users?email=john&page=0&size=10")
                .withAuth(admin.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].email").value("john@test.com"))
    }

    @Test
    fun `updates user`() {
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val user = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "old@test.com",
                createdAt = Instant.now()
            )
        )

        val payload = UserUpdateRequest(
            email = "new@test.com",
            attrs = """{"updated":true}""",
            firstName = "Петр",
            lastName = "Петров",
            middleName = "Петрович",
            position = "Техлид"
        )

        mockMvc.perform(
            put("/api/v1/users/${user.id}")
                .withAuth(admin.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("new@test.com"))
            .andExpect(jsonPath("$.attrs").isNotEmpty)
            .andExpect(jsonPath("$.firstName").value("Петр"))
            .andExpect(jsonPath("$.lastName").value("Петров"))
            .andExpect(jsonPath("$.position").value("Техлид"))
    }

    @Test
    fun `admin can update user password`() {
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val user = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "password@test.com",
                passwordHash = passwordEncoder.encode("oldpass123"),
                createdAt = Instant.now()
            )
        )

        val payload = UserUpdateRequest(password = "newpass123")

        mockMvc.perform(
            put("/api/v1/users/${user.id}")
                .withAuth(admin.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("password@test.com"))

        val updated = usersRepository.findById(user.id!!).orElseThrow()
        assertTrue(passwordEncoder.matches("newpass123", updated.passwordHash))
        assertFalse(passwordEncoder.matches("oldpass123", updated.passwordHash))
    }

    @Test
    fun `returns public user info for authenticated user`() {
        val requester = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "requester@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val target = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "public@test.com",
                attrs = """{"firstName":"Анна","lastName":"Смирнова","position":"Архитектор"}""",
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/users/${target.id}/public")
                .withAuth(requester.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("public@test.com"))
            .andExpect(jsonPath("$.firstName").value("Анна"))
            .andExpect(jsonPath("$.lastName").value("Смирнова"))
            .andExpect(jsonPath("$.position").value("Архитектор"))
    }

    @Test
    fun `user can update own profile`() {
        val user = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "self@test.com",
                attrs = """{"firstName":"Старое","lastName":"Имя","position":"Стажер"}""",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        val payload = UserProfileUpdateRequest(
            firstName = "Новое",
            lastName = "Имя",
            middleName = "Отчество",
            position = "Инженер"
        )

        mockMvc.perform(
            put("/api/v1/users/me/profile")
                .withAuth(user.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("self@test.com"))
            .andExpect(jsonPath("$.firstName").value("Новое"))
            .andExpect(jsonPath("$.middleName").value("Отчество"))
            .andExpect(jsonPath("$.position").value("Инженер"))
    }

    @Test
    fun `deletes user`() {
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val user = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "delete@test.com",
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            delete("/api/v1/users/${user.id}")
                .withAuth(admin.id!!)
        )
            .andExpect(status().isNoContent)

        assertEquals(1, usersRepository.count())
    }

    @Test
    fun `returns 403 for non-admin user`() {
        val user = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/users?page=0&size=10")
                .withAuth(user.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }
}
