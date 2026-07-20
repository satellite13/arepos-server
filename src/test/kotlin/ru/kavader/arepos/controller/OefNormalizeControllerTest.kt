package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class OefNormalizeControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Test
    fun `owner can normalize oef xml`() {
        val owner = persistUser("oef-owner@test.com")
        val model = persistModel(owner)
        val upload = fixtureUpload()

        mockMvc.perform(
            multipart("/api/v1/models/${model.id}/oef/normalize")
                .file(upload)
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.model.id").value("id-model-1"))
            .andExpect(jsonPath("$.elements.length()").value(3))
            .andExpect(jsonPath("$.relationships.length()").value(2))
            .andExpect(jsonPath("$.views.length()").value(1))
            .andExpect(jsonPath("$.issues[?(@.code=='relationshipEndpointIsRelationship')]").exists())
    }

    @Test
    fun `other user cannot normalize`() {
        val owner = persistUser("oef-acl-owner@test.com")
        val other = persistUser("oef-acl-other@test.com")
        val model = persistModel(owner)

        mockMvc.perform(
            multipart("/api/v1/models/${model.id}/oef/normalize")
                .file(fixtureUpload())
                .withAuth(other.id!!, Role.USER)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `invalid xml returns 400`() {
        val owner = persistUser("oef-bad@test.com")
        val model = persistModel(owner)
        val upload = MockMultipartFile(
            "file",
            "bad.xml",
            MediaType.APPLICATION_XML_VALUE,
            "<root />".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/models/${model.id}/oef/normalize")
                .file(upload)
                .withAuth(owner.id!!, Role.USER)
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `missing model returns 404`() {
        val owner = persistUser("oef-missing@test.com")
        mockMvc.perform(
            multipart("/api/v1/models/00000000-0000-0000-0000-000000000001/oef/normalize")
                .file(fixtureUpload())
                .withAuth(owner.id!!, Role.USER)
        ).andExpect(status().isNotFound)
    }

    private fun persistUser(email: String): Users =
        usersRepository.save(
            Users(
                email = email,
                role = Role.USER,
                createdAt = Instant.now(),
            )
        )

    private fun persistModel(owner: Users): Models =
        modelsRepository.save(
            Models(
                name = "oef-model",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        )

    private fun fixtureUpload(): MockMultipartFile {
        val bytes = checkNotNull(javaClass.classLoader.getResourceAsStream("oef/container-assoc-to-flow.xml"))
            .use { it.readBytes() }
        return MockMultipartFile(
            "file",
            "container-assoc-to-flow.xml",
            MediaType.APPLICATION_XML_VALUE,
            bytes,
        )
    }
}
