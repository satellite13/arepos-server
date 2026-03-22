package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AccessListInvariantsTest : ControllerIntegrationTest() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Autowired
    lateinit var componentsRepository: ComponentsRepository

    @Test
    fun `models list keeps access and pagination invariants`() {
        val now = Instant.now()
        val owner = createUser("owner-model-inv", Role.USER)
        val sharedUser = createUser("shared-model-inv", Role.USER)
        val foreign = createUser("foreign-model-inv", Role.USER)

        val ownModel = modelsRepository.save(
            Models(name = "own-model", createdAt = now, updatedAt = now, version = "1.0.0", owner = sharedUser)
        )
        val sharedModel = modelsRepository.save(
            Models(name = "shared-model", createdAt = now, updatedAt = now, version = "1.0.0", owner = owner)
        )
        modelsRepository.save(
            Models(name = "foreign-model", createdAt = now, updatedAt = now, version = "1.0.0", owner = foreign)
        )

        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = sharedModel.id!!,
                granteeUser = sharedUser,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = now
            )
        )

        val response = mockMvc.perform(
            get("/api/v1/models?page=0&size=10")
                .withAuth(sharedUser.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val json = objectMapper.readTree(response)
        val ids = contentIds(json)
        assertEquals(2, json.path("page").path("totalElements").asInt())
        assertTrue(ids.contains(ownModel.id.toString()))
        assertTrue(ids.contains(sharedModel.id.toString()))
    }

    @Test
    fun `notations list keeps owner filter semantics for shared users`() {
        val now = Instant.now()
        val owner = createUser("owner-notation-inv", Role.USER)
        val viewer = createUser("viewer-notation-inv", Role.USER)
        val otherOwner = createUser("other-notation-inv", Role.USER)

        val sharedNotation = notationsRepository.save(
            Notations(name = "shared-notation", version = "1.0.0", owner = owner, createdAt = now, updatedAt = now)
        )
        notationsRepository.save(
            Notations(name = "foreign-notation", version = "1.0.0", owner = otherOwner, createdAt = now, updatedAt = now)
        )

        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.NOTATION,
                resourceId = sharedNotation.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = now
            )
        )

        mockMvc.perform(
            get("/api/v1/notations?ownerId=${owner.id}&page=0&size=10")
                .withAuth(viewer.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                val body = objectMapper.readTree(result.response.contentAsString)
                assertEquals(1, body.path("page").path("totalElements").asInt())
                val ids = contentIds(body)
                assertTrue(ids.contains(sharedNotation.id.toString()))
            }
    }

    @Test
    fun `components list includes notation from editable model diagrams`() {
        val now = Instant.now()
        val notationOwner = createUser("notation-owner-inv", Role.USER)
        val modelOwner = createUser("model-owner-inv", Role.USER)
        val editor = createUser("editor-inv", Role.USER)

        val notation = notationsRepository.save(
            Notations(name = "diagram-notation", version = "1.0.0", owner = notationOwner, createdAt = now, updatedAt = now)
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(name = "diagram-node-type-${UUID.randomUUID()}", createdAt = now, updatedAt = now, owner = notationOwner)
        )
        val model = modelsRepository.save(
            Models(name = "diagram-model", version = "1.0.0", owner = modelOwner, createdAt = now, updatedAt = now)
        )
        diagramsRepository.save(
            Diagrams(
                name = "diag",
                version = "1.0.0",
                owner = modelOwner,
                createdAt = now,
                updatedAt = now,
                model = model,
                notation = notation,
                deleted = false
            )
        )

        val persisted = componentsRepository.save(
            ru.kavader.arepos.model.Components(
            name = "diagram-component",
            version = "1.0.0",
            notation = notation,
            owner = notationOwner,
            nodeType = nodeType,
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
                createdAt = now
            )
        )

        val response = mockMvc.perform(
            get("/api/v1/components?page=0&size=10")
                .withAuth(editor.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val json = objectMapper.readTree(response)
        val ids = contentIds(json)
        assertTrue(ids.contains(persisted.id.toString()))
    }

    @Test
    fun `link types list respects shared and query filters`() {
        val now = Instant.now()
        val owner = createUser("owner-link-type-inv", Role.USER)
        val viewer = createUser("viewer-link-type-inv", Role.USER)

        val sharedLinkType = linkTypesRepository.save(
            LinkTypes(name = "Shared Link Type ${UUID.randomUUID()}", owner = owner, createdAt = now, updatedAt = now)
        )
        linkTypesRepository.save(
            LinkTypes(name = "Other Link Type ${UUID.randomUUID()}", owner = owner, createdAt = now, updatedAt = now)
        )

        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.LINK_TYPE,
                resourceId = sharedLinkType.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = now
            )
        )

        val response = mockMvc.perform(
            get("/api/v1/link-types?name=Shared&page=0&size=10")
                .withAuth(viewer.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val json = objectMapper.readTree(response)
        assertEquals(1, json.path("page").path("totalElements").asInt())
        val ids = contentIds(json)
        assertTrue(ids.contains(sharedLinkType.id.toString()))
    }

    private fun createUser(prefix: String, role: Role): Users =
        usersRepository.save(
            Users(
                email = "$prefix-${UUID.randomUUID()}@test.com",
                role = role,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

    private fun contentIds(json: JsonNode): Set<String> =
        json.path("content")
            .mapNotNull { node -> node.path("id").asText().takeIf { it.isNotBlank() } }
            .toSet()
}
