package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.featuregrant.FeatureGrantsUpdateRequest
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.CurrentUser
import ru.kavader.arepos.security.ResourceAccessService
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AdminFeatureGrantsControllerTest : ControllerIntegrationTest() {

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
    fun `non-admin cannot put role feature grants`() {
        val regularUser = usersRepository.save(
            Users(
                email = "feature-grants-forbidden@test.com",
                role = Role.reader,
                createdAt = Instant.now(),
            )
        )

        mockMvc.perform(
            put("/api/v1/admin/role-feature-grants/editor")
                .withAuth(regularUser.id!!, Role.reader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        FeatureGrantsUpdateRequest(grants = listOf("model.export"))
                    )
                )
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin can put editor role feature grants`() {
        val admin = usersRepository.save(
            Users(
                email = "feature-grants-admin@test.com",
                role = Role.admin,
                createdAt = Instant.now(),
            )
        )

        val previousEditorGrants = objectMapper.readTree(
            mockMvc.perform(
                get("/api/v1/admin/role-feature-grants")
                    .withAuth(admin.id!!, Role.admin)
            )
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        ).path("roles").path("editor").map { it.asText() }

        try {
            mockMvc.perform(
                put("/api/v1/admin/role-feature-grants/editor")
                    .withAuth(admin.id!!, Role.admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            FeatureGrantsUpdateRequest(
                                grants = listOf("model.export", "notation.nav")
                            )
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.grants.length()").value(2))
                .andExpect(jsonPath("$.grants[0]").value("model.export"))
                .andExpect(jsonPath("$.grants[1]").value("notation.nav"))

            mockMvc.perform(
                get("/api/v1/admin/role-feature-grants")
                    .withAuth(admin.id!!, Role.admin)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.roles.editor.length()").value(2))
                .andExpect(jsonPath("$.roles.editor[0]").value("model.export"))
                .andExpect(jsonPath("$.roles.editor[1]").value("notation.nav"))
                .andExpect(jsonPath("$.catalog").isArray)
                .andExpect(jsonPath("$.roles.admin").doesNotExist())
        } finally {
            mockMvc.perform(
                put("/api/v1/admin/role-feature-grants/editor")
                    .withAuth(admin.id!!, Role.admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            FeatureGrantsUpdateRequest(grants = previousEditorGrants)
                        )
                    )
            ).andExpect(status().isOk)
        }
    }

    @Test
    fun `put admin role feature grants returns 400`() {
        val admin = usersRepository.save(
            Users(
                email = "feature-grants-admin-reject@test.com",
                role = Role.admin,
                createdAt = Instant.now(),
            )
        )

        mockMvc.perform(
            put("/api/v1/admin/role-feature-grants/admin")
                .withAuth(admin.id!!, Role.admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        FeatureGrantsUpdateRequest(grants = listOf("model.export"))
                    )
                )
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `put user feature grant allows for missing user returns 404`() {
        val admin = usersRepository.save(
            Users(
                email = "feature-grants-missing-user-admin@test.com",
                role = Role.admin,
                createdAt = Instant.now(),
            )
        )
        val missingId = UUID.randomUUID()

        mockMvc.perform(
            put("/api/v1/admin/users/$missingId/feature-grant-allows")
                .withAuth(admin.id!!, Role.admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        FeatureGrantsUpdateRequest(grants = listOf("model.export"))
                    )
                )
        ).andExpect(status().isNotFound)
    }
}
