package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.model.ModelRequest
import ru.kavader.arepos.dto.model.ModelResponse
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.repository.*
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ModelsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var linksRepository: LinksRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Test
    fun `creates model via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        val payload = ModelRequest(
            name = "rest-model",
            version = "1.0.0",
            ownerId = owner.id!!,
            attrs = """{"key":"value"}"""
        )

        mockMvc.perform(
            post("/api/v1/models")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("rest-model"))
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))

        assertEquals(1, modelsRepository.count())
        assertEquals(1, nodesRepository.count())

        val createdModel = modelsRepository.findAll().first()
        val attrsNode = objectMapper.readTree(createdModel.attrs ?: "{}")
        val rootNodeIdRaw = attrsNode.path("treeRootNodeId").asText()
        assertNotNull(rootNodeIdRaw.takeIf { it.isNotBlank() })
        val rootNodeId = UUID.fromString(rootNodeIdRaw)
        val rootNode = nodesRepository.findById(rootNodeId).orElseThrow()
        assertEquals(createdModel.id, rootNode.model.id)
    }

    @Test
    fun `lists models`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-list@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        modelsRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.Models(
                    name = "model-1",
                    createdAt = Instant.now(),
                    version = "1.0.0",
                    owner = owner
                ),
                ru.kavader.arepos.model.Models(
                    name = "model-2",
                    createdAt = Instant.now(),
                    version = "1.0.1",
                    owner = owner
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/models?page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(2))
    }

    @Test
    fun `user sees only own models`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-a@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-b@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val ownModel = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "own-model",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = userA
            )
        )
        modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "foreign-model",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = userB
            )
        )

        mockMvc.perform(
            get("/api/v1/models?page=0&size=10")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(ownModel.id.toString()))
    }

    @Test
    fun `user cannot read foreign model by id`() {
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
        val foreignModel = modelsRepository.save(
            ru.kavader.arepos.model.Models(
                name = "foreign-model",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = userB
            )
        )

        mockMvc.perform(
            get("/api/v1/models/${foreignModel.id}")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `copy model remaps diagram edge modelLinkId`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "copy-owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        val sourcePayload = ModelRequest(
            name = "copy-source-model",
            version = "1.0.0",
            ownerId = owner.id!!,
            attrs = "{}"
        )

        val sourceResponse = mockMvc.perform(
            post("/api/v1/models")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sourcePayload))
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        val sourceModel = objectMapper.readValue(sourceResponse, ModelResponse::class.java)

        val sourceModelEntity = modelsRepository.findById(sourceModel.id).orElseThrow()
        val sourceRootNode = nodesRepository.findByModelIdOrdered(sourceModel.id, Pageable.unpaged()).content
            .first { it.parentNode == null }
        val nodeType = sourceRootNode.nodeType
        val now = Instant.now()

        val sourceNodeA = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = UUID.randomUUID(),
                name = "Node A",
                createdAt = now,
                updatedAt = now,
                attrs = "{}",
                parentNode = sourceRootNode,
                model = sourceModelEntity,
                owner = owner,
                nodeType = nodeType
            )
        )
        val sourceNodeB = nodesRepository.save(
            ru.kavader.arepos.model.Nodes(
                stableId = UUID.randomUUID(),
                name = "Node B",
                createdAt = now,
                updatedAt = now,
                attrs = "{}",
                parentNode = sourceRootNode,
                model = sourceModelEntity,
                owner = owner,
                nodeType = nodeType
            )
        )

        val linkType = linkTypesRepository.save(
            ru.kavader.arepos.model.LinkTypes(
                name = "Copy Test LinkType",
                createdAt = now,
                updatedAt = now,
                attrs = "{}",
                owner = owner
            )
        )
        val sourceLink = linksRepository.save(
            ru.kavader.arepos.model.Links(
                stableId = UUID.randomUUID(),
                source = sourceNodeA,
                target = sourceNodeB,
                attrs = "{}",
                createdAt = now,
                updatedAt = now,
                owner = owner,
                linkType = linkType,
                model = sourceModelEntity
            )
        )

        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                owner = owner,
                attrs = "{}",
                createdAt = now,
                updatedAt = now,
                name = "Copy Test Notation",
                version = "1.0.0",
                deleted = false
            )
        )
        val diagramAttrs = """
            {
              "instances": {
                "nodes": [
                  { "id": "inst-a", "modelNodeId": "${sourceNodeA.id}", "x": 100, "y": 100 },
                  { "id": "inst-b", "modelNodeId": "${sourceNodeB.id}", "x": 300, "y": 100 }
                ],
                "edges": [
                  {
                    "id": "edge-1",
                    "modelLinkId": "${sourceLink.id}",
                    "sourceInstanceId": "inst-a",
                    "targetInstanceId": "inst-b"
                  }
                ]
              }
            }
        """.trimIndent()
        diagramsRepository.save(
            ru.kavader.arepos.model.Diagrams(
                name = "Main Diagram",
                createdAt = now,
                updatedAt = now,
                attrs = diagramAttrs,
                version = "1.0.0",
                owner = owner,
                deleted = false,
                model = sourceModelEntity,
                notation = notation,
                node = null
            )
        )

        val copyPayload = ModelRequest(
            name = sourceModel.name,
            version = "1.1.0",
            ownerId = owner.id!!,
            attrs = "{}"
        )
        val copyResponse = mockMvc.perform(
            post("/api/v1/models/${sourceModel.id}/copy")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(copyPayload))
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        val copiedModel = objectMapper.readValue(copyResponse, ModelResponse::class.java)
        val copiedModelEntity = modelsRepository.findById(copiedModel.id).orElseThrow()

        val copiedLinks = linksRepository.findByModel(copiedModelEntity, Pageable.unpaged()).content
        assertEquals(1, copiedLinks.size)
        val copiedLinkId = copiedLinks.first().id!!.toString()

        val copiedDiagrams = diagramsRepository.findByFilters(
            ownerId = null,
            modelId = copiedModel.id,
            nodeId = null,
            notationId = null,
            name = "",
            pageable = Pageable.unpaged()
        ).content
        assertEquals(1, copiedDiagrams.size)
        val copiedDiagramAttrs = objectMapper.readTree(copiedDiagrams.first().attrs)
        val remappedModelLinkId = copiedDiagramAttrs
            .path("instances")
            .path("edges")
            .path(0)
            .path("modelLinkId")
            .asText()

        assertEquals(copiedLinkId, remappedModelLinkId)
        assertTrue(remappedModelLinkId != sourceLink.id.toString())
    }
}
