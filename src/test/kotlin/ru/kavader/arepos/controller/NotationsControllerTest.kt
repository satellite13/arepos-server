package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.notation.NotationRequest
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.*
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class NotationsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Test
    fun `creates notation via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        val payload = NotationRequest(
            name = "test-notation",
            version = "1.0.0",
            ownerId = owner.id!!,
            attrs = """{"type":"test"}"""
        )

        mockMvc.perform(
            post("/api/v1/notations")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("test-notation"))
            .andExpect(jsonPath("$.version").value("1.0.0"))

        assertEquals(1, notationsRepository.count())
    }

    @Test
    fun `lists notations with filters`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        notationsRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Notations(
                    name = "notation-1",
                    version = "1.0.0",
                    owner = owner,
                    createdAt = Instant.now()
                ),
                ru.kavader.arepos.model.Notations(
                    name = "notation-2",
                    version = "1.0.1",
                    owner = owner,
                    createdAt = Instant.now()
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/notations?ownerId=${owner.id}&page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(2))
    }

    @Test
    fun `user sees only own notations`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-a@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-b@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val ownNotation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "own-notation",
                version = "1.0.0",
                owner = userA,
                createdAt = Instant.now()
            )
        )
        notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "foreign-notation",
                version = "1.0.0",
                owner = userB,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/notations?page=0&size=10")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(ownNotation.id.toString()))
    }

    @Test
    fun `user cannot read foreign notation by id`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "reader-a@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "reader-b@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val foreignNotation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "foreign-notation",
                version = "1.0.0",
                owner = userB,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/notations/${foreignNotation.id}")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `user with model edit access can read foreign notation only when used in model diagrams`() {
        val now = Instant.now()
        val notationOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-owner-usage@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val modelOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "model-owner-usage@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val editor = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "model-editor-usage@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val usedNotation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "shared-by-model-usage",
                version = "1.0.0",
                owner = notationOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "model-usage",
                version = "1.0.0",
                owner = modelOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        diagramsRepository.save(
            Diagrams(
                name = "diagram-usage",
                version = "1.0.0",
                owner = modelOwner,
                model = model,
                notation = usedNotation,
                createdAt = now,
                updatedAt = now
            )
        )
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = editor,
                grantedByUser = modelOwner,
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(
            get("/api/v1/notations/${usedNotation.id}?modelId=${model.id}")
                .withAuth(editor.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(usedNotation.id.toString()))
    }

    @Test
    fun `user with model edit access cannot read unrelated foreign notation`() {
        val now = Instant.now()
        val notationOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-owner-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val modelOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "model-owner-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val editor = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "model-editor-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val unrelatedNotation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "foreign-unrelated-notation",
                version = "1.0.0",
                owner = notationOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "model-unrelated",
                version = "1.0.0",
                owner = modelOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = editor,
                grantedByUser = modelOwner,
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(
            get("/api/v1/notations/${unrelatedNotation.id}?modelId=${model.id}")
                .withAuth(editor.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `shared user can create notation version from source copy`() {
        val now = Instant.now()
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-owner-copy@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val viewer = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "notation-viewer-copy@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val source = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "shared-notation-copy",
                version = "1.0.0",
                owner = owner,
                createdAt = now,
                updatedAt = now
            )
        )

        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.NOTATION,
                resourceId = source.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.EDIT,
                createdAt = now,
                updatedAt = now
            )
        )

        val payload = NotationRequest(
            name = source.name,
            version = "1.1.0",
            ownerId = viewer.id
        )

        mockMvc.perform(
            post("/api/v1/notations/${source.id}/copy")
                .withAuth(viewer.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("shared-notation-copy"))
            .andExpect(jsonPath("$.version").value("1.1.0"))
            .andExpect(jsonPath("$.ownerId").value(viewer.id.toString()))
    }
}
