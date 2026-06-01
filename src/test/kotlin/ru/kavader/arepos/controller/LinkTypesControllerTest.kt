package ru.kavader.arepos.controller
import ru.kavader.arepos.dto.notation.*

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
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.RelationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class LinkTypesControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var relationsRepository: RelationsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Test
    fun `creates link type via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-link-type@test.com",
                role = Role.EDITOR,
                createdAt = Instant.now()
            )
        )

        val payload = LinkTypeRequest(
            name = "test-link-type-${System.currentTimeMillis()}",
            ownerId = owner.id!!,
            attrs = """{"key":"value"}"""
        )

        mockMvc.perform(
            post("/api/v1/link-types")
                .withAuth(owner.id!!, Role.EDITOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value(payload.name))
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))

        assertEquals(1, linkTypesRepository.count())
    }

    @Test
    fun `lists link types`() {
        val timestamp = System.currentTimeMillis()
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-list-link-type-$timestamp@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        linkTypesRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.LinkTypes(
                    name = "link-type-1-$timestamp",
                    createdAt = Instant.now(),
                    owner = owner
                ),
                ru.kavader.arepos.model.LinkTypes(
                    name = "link-type-2-$timestamp",
                    createdAt = Instant.now(),
                    owner = owner
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/link-types?page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(2))
    }

    @Test
    fun `user sees only own link types`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-type-a@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-type-b@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val ownType = linkTypesRepository.save(
            ru.kavader.arepos.model.LinkTypes(
                name = "own-link-type",
                createdAt = Instant.now(),
                owner = userA
            )
        )
        linkTypesRepository.save(
            ru.kavader.arepos.model.LinkTypes(
                name = "foreign-link-type",
                createdAt = Instant.now(),
                owner = userB
            )
        )

        mockMvc.perform(
            get("/api/v1/link-types?page=0&size=10")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(ownType.id.toString()))
    }

    @Test
    fun `user can create own link type`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-create-link-type@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val payload = LinkTypeRequest(
            name = "user-link-type-${System.currentTimeMillis()}",
            ownerId = owner.id!!,
            attrs = """{"scope":"own"}"""
        )

        mockMvc.perform(
            post("/api/v1/link-types")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))
            .andExpect(jsonPath("$.name").value(payload.name))
    }

    @Test
    fun `ignores foreign ownerId for non-admin, creates under own identity`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-a-link-type-create@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-b-link-type-create@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val payload = LinkTypeRequest(
            name = "owned-link-type-${System.currentTimeMillis()}",
            ownerId = userB.id!!,
            attrs = null
        )

        mockMvc.perform(
            post("/api/v1/link-types")
                .withAuth(userA.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerId").value(userA.id.toString()))
    }

    @Test
    fun `list link types allows notation filter for editable model when notation used by diagram`() {
        val now = Instant.now()
        val notationOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-notation-owner@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val modelOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-model-owner@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val editor = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-model-editor@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "link-notation-used",
                version = "1.0.0",
                owner = notationOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "link-model-used",
                version = "1.0.0",
                owner = modelOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val linkType = linkTypesRepository.save(
            ru.kavader.arepos.model.LinkTypes(
                name = "link-type-from-notation",
                owner = notationOwner,
                createdAt = now
            )
        )
        diagramsRepository.save(
            Diagrams(
                name = "link-diagram-used",
                version = "1.0.0",
                owner = modelOwner,
                model = model,
                notation = notation,
                createdAt = now,
                updatedAt = now
            )
        )
        relationsRepository.save(
            Relations(
                name = "link-relation",
                version = "1.0.0",
                owner = notationOwner,
                notation = notation,
                linkType = linkType,
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
            get("/api/v1/link-types?notationId=${notation.id}&modelId=${model.id}&page=0&size=10")
                .withAuth(editor.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(linkType.id.toString()))
    }

    @Test
    fun `list link types denies unrelated notation even with model edit access`() {
        val now = Instant.now()
        val notationOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-notation-owner-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val modelOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-model-owner-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val editor = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "link-model-editor-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "link-notation-unrelated",
                version = "1.0.0",
                owner = notationOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "link-model-unrelated",
                version = "1.0.0",
                owner = modelOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val linkType = linkTypesRepository.save(
            ru.kavader.arepos.model.LinkTypes(
                name = "link-type-unrelated",
                owner = notationOwner,
                createdAt = now
            )
        )
        relationsRepository.save(
            Relations(
                name = "link-relation-unrelated",
                version = "1.0.0",
                owner = notationOwner,
                notation = notation,
                linkType = linkType,
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
            get("/api/v1/link-types?notationId=${notation.id}&modelId=${model.id}&page=0&size=10")
                .withAuth(editor.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
    }
}
