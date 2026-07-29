package ru.kavader.arepos.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.model.ValidationScripts
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.repository.ValidationScriptsRepository
import ru.kavader.arepos.model.ResourceShares
import java.time.Instant
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
class ValidationScriptsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var validationScriptsRepository: ValidationScriptsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    private lateinit var owner: Users
    private lateinit var outsider: Users
    private lateinit var ownerScript: ValidationScripts
    private lateinit var outsiderScript: ValidationScripts

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            Users(
                email = "validation-script-owner-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        outsider = usersRepository.save(
            Users(
                email = "validation-script-outsider-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        ownerScript = validationScriptsRepository.save(
            ValidationScripts(
                name = "owner-script-${UUID.randomUUID()}",
                description = "owner desc",
                source = "report.info('ok')",
                owner = owner,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        outsiderScript = validationScriptsRepository.save(
            ValidationScripts(
                name = "outsider-script-${UUID.randomUUID()}",
                source = "report.warn('x')",
                owner = outsider,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
    }

    @Test
    fun `denies get for non-owner without share`() {
        mockMvc.perform(
            get("/api/v1/validation-scripts/${ownerScript.id}")
                .withAuth(outsider.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `allows get for owner`() {
        mockMvc.perform(
            get("/api/v1/validation-scripts/${ownerScript.id}")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(ownerScript.id.toString()))
            .andExpect(jsonPath("$.source").value("report.info('ok')"))
    }

    @Test
    fun `allows get for view share`() {
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.VALIDATION_SCRIPT,
                resourceId = ownerScript.id!!,
                granteeUser = outsider,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = Instant.now()
            )
        )
        mockMvc.perform(
            get("/api/v1/validation-scripts/${ownerScript.id}")
                .withAuth(outsider.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(ownerScript.id.toString()))
    }

    @Test
    fun `list returns only visible scripts`() {
        mockMvc.perform(
            get("/api/v1/validation-scripts?page=0&size=10")
                .withAuth(outsider.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(outsiderScript.id.toString()))
    }

    @Test
    fun `create rejects blank source`() {
        mockMvc.perform(
            post("/api/v1/validation-scripts")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"blank-source","source":"   "}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create and update and delete for owner`() {
        val createResult = mockMvc.perform(
            post("/api/v1/validation-scripts")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"created-${UUID.randomUUID()}","description":"d","source":"report.info('a')"}
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.source").value("report.info('a')"))
            .andReturn()

        val id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)
            .get("id")
            .asText()

        mockMvc.perform(
            put("/api/v1/validation-scripts/$id")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"source":"report.warn('b')"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source").value("report.warn('b')"))

        mockMvc.perform(
            delete("/api/v1/validation-scripts/$id")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/validation-scripts/$id")
                .withAuth(owner.id!!, Role.USER)
        )
            .andExpect(status().isNotFound)
    }
}
