package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.access.AccessShareRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class AccessSharesControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Test
    fun `owner grants lists and revokes model share`() {
        val owner = persistUser("share-owner@test.com")
        val grantee = persistUser("share-grantee@test.com")
        val model = persistModel(owner, "shared-model")

        mockMvc.perform(
            get("/api/v1/models/${model.id}")
                .withAuth(grantee.id!!, Role.USER)
        ).andExpect(status().isForbidden)

        val request = AccessShareRequest(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id,
            granteeUserId = grantee.id,
            permission = SharePermission.VIEW
        )
        val shareId = mockMvc.perform(
            post("/api/v1/access/shares")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resourceType").value("MODEL"))
            .andExpect(jsonPath("$.resourceId").value(model.id.toString()))
            .andExpect(jsonPath("$.granteeUserId").value(grantee.id.toString()))
            .andExpect(jsonPath("$.permission").value("VIEW"))
            .andReturn()
            .response
            .let { objectMapper.readTree(it.contentAsString).path("id").asText() }

        mockMvc.perform(
            get("/api/v1/access/shares/MODEL/${model.id}")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(shareId))

        mockMvc.perform(
            get("/api/v1/models/${model.id}")
                .withAuth(grantee.id!!, Role.USER)
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/api/v1/access/shares/$shareId")
                .withAuth(owner.id!!, Role.USER)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/models/${model.id}")
                .withAuth(grantee.id!!, Role.USER)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `non owner cannot grant or list model shares`() {
        val owner = persistUser("share-deny-owner@test.com")
        val other = persistUser("share-deny-other@test.com")
        val grantee = persistUser("share-deny-grantee@test.com")
        val model = persistModel(owner, "private-model")
        val request = AccessShareRequest(
            resourceType = ShareResourceType.MODEL,
            resourceId = model.id,
            granteeUserId = grantee.id,
            permission = SharePermission.EDIT
        )

        mockMvc.perform(
            post("/api/v1/access/shares")
                .withAuth(other.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            get("/api/v1/access/shares/MODEL/${model.id}")
                .withAuth(other.id!!, Role.USER)
        ).andExpect(status().isForbidden)
    }

    private fun persistUser(email: String): Users =
        usersRepository.save(
            Users(
                email = email,
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

    private fun persistModel(owner: Users, name: String): Models =
        modelsRepository.save(
            Models(
                name = name,
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
}
