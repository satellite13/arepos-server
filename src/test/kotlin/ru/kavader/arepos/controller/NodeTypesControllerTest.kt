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
import ru.kavader.arepos.dto.notation.NodeTypeRequest
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.*
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class NodeTypesControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var componentsRepository: ComponentsRepository

    @Autowired
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Test
    fun `creates node type via REST`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-node-type@test.com",
                role = Role.EDITOR,
                createdAt = Instant.now()
            )
        )

        val payload = NodeTypeRequest(
            name = "test-node-type-${System.currentTimeMillis()}",
            ownerId = owner.id!!,
            attrs = """{"key":"value"}"""
        )

        mockMvc.perform(
            post("/api/v1/node-types")
                .withAuth(owner.id!!, Role.EDITOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value(payload.name))
            .andExpect(jsonPath("$.ownerId").value(owner.id.toString()))

        assertEquals(1, nodeTypesRepository.count())
    }

    @Test
    fun `allows creating same node type name for different owners`() {
        val ownerA = usersRepository.save(
            Users(
                email = "owner-a-same-name@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val ownerB = usersRepository.save(
            Users(
                email = "owner-b-same-name@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        nodeTypesRepository.save(
            NodeTypes(
                name = "Application Function",
                owner = ownerA,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            post("/api/v1/node-types")
                .withAuth(ownerB.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        NodeTypeRequest(
                            name = "Application Function",
                            ownerId = ownerB.id!!,
                            attrs = null
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Application Function"))
            .andExpect(jsonPath("$.ownerId").value(ownerB.id.toString()))

        assertEquals(2, nodeTypesRepository.count())
    }

    @Test
    fun `returns 409 when same owner creates duplicate node type name`() {
        val owner = usersRepository.save(
            Users(
                email = "owner-dup-name@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        nodeTypesRepository.save(
            NodeTypes(
                name = "Application Function",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            post("/api/v1/node-types")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        NodeTypeRequest(
                            name = "application function",
                            ownerId = owner.id!!,
                            attrs = null
                        )
                    )
                )
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `lists node types`() {
        val timestamp = System.currentTimeMillis()
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "owner-list-node-type-$timestamp@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        nodeTypesRepository.saveAll(
            listOf(
                ru.kavader.arepos.model.NodeTypes(
                    name = "node-type-1-$timestamp",
                    createdAt = Instant.now(),
                    owner = owner
                ),
                ru.kavader.arepos.model.NodeTypes(
                    name = "node-type-2-$timestamp",
                    createdAt = Instant.now(),
                    owner = owner
                )
            )
        )

        mockMvc.perform(
            get("/api/v1/node-types?page=0&size=10")
                .withAuth(owner.id!!)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(2))
    }

    @Test
    fun `user sees only own node types`() {
        val userA = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-type-a@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-type-b@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val ownType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "own-node-type",
                createdAt = Instant.now(),
                owner = userA
            )
        )
        nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "foreign-node-type",
                createdAt = Instant.now(),
                owner = userB
            )
        )

        mockMvc.perform(
            get("/api/v1/node-types?page=0&size=10")
                .withAuth(userA.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(ownType.id.toString()))
    }

    @Test
    fun `user can create own node type`() {
        val owner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-create-node-type@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val payload = NodeTypeRequest(
            name = "user-node-type-${System.currentTimeMillis()}",
            ownerId = owner.id!!,
            attrs = """{"scope":"own"}"""
        )

        mockMvc.perform(
            post("/api/v1/node-types")
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
                email = "user-a-node-type-create@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val userB = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "user-b-node-type-create@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        val payload = NodeTypeRequest(
            name = "owned-node-type-${System.currentTimeMillis()}",
            ownerId = userB.id!!,
            attrs = null
        )

        mockMvc.perform(
            post("/api/v1/node-types")
                .withAuth(userA.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerId").value(userA.id.toString()))
    }

    @Test
    fun `directory node type is readable for any user`() {
        val systemUser = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "system-public@test.com",
                role = Role.USER,
                isActive = false,
                createdAt = Instant.now()
            )
        )
        val directoryType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "Directory",
                attrs = """{"system":true,"kind":"directory"}""",
                createdAt = Instant.now(),
                owner = systemUser
            )
        )
        val regularUser = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "regular-reader@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            get("/api/v1/node-types/${directoryType.id}")
                .withAuth(regularUser.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Directory"))
    }

    @Test
    fun `admin cannot soft-delete or permanently delete system Directory`() {
        val systemUser = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "system@arepos.local",
                role = Role.USER,
                isActive = false,
                createdAt = Instant.now()
            )
        )
        val directoryType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "Directory",
                attrs = """{"system":{"hiddenTreeRootType":true}}""",
                createdAt = Instant.now(),
                owner = systemUser
            )
        )
        val admin = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "admin-directory-guard@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            delete("/api/v1/node-types/${directoryType.id}")
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            delete("/api/v1/node-types/${directoryType.id}/permanent")
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            get("/api/v1/node-types")
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.name=='Directory')]").isEmpty)
    }

    @Test
    fun `list node types with modelId includes system Directory used by model nodes`() {
        val now = Instant.now()
        val systemUser = usersRepository.save(
            Users(
                email = "system@arepos.local",
                role = Role.USER,
                isActive = false,
                createdAt = now
            )
        )
        val admin = usersRepository.save(
            Users(
                email = "admin-directory-modelid@test.com",
                role = Role.ADMIN,
                createdAt = now
            )
        )
        val directoryType = nodeTypesRepository.save(
            NodeTypes(
                name = "Directory",
                attrs = """{"system":{"hiddenTreeRootType":true}}""",
                createdAt = now,
                updatedAt = now,
                owner = systemUser
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "Directory Model Scope",
                version = "1.0.0",
                createdAt = now,
                updatedAt = now,
                owner = admin
            )
        )
        nodesRepository.save(
            Nodes(
                stableId = java.util.UUID.randomUUID(),
                name = "Root",
                createdAt = now,
                updatedAt = now,
                parentNode = null,
                model = model,
                owner = admin,
                nodeType = directoryType
            )
        )

        mockMvc.perform(
            get("/api/v1/node-types")
                .param("modelId", model.id!!.toString())
                .withAuth(admin.id!!, Role.ADMIN)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.name=='Directory')]").isNotEmpty)
    }

    @Test
    fun `list node types allows notation filter for editable model when notation used by diagram`() {
        val now = Instant.now()
        val notationOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-notation-owner@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val modelOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-model-owner@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val editor = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-model-editor@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "node-notation-used",
                version = "1.0.0",
                owner = notationOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "node-model-used",
                version = "1.0.0",
                owner = modelOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val nodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "node-type-from-notation",
                owner = notationOwner,
                createdAt = now
            )
        )
        diagramsRepository.save(
            Diagrams(
                name = "node-diagram-used",
                version = "1.0.0",
                owner = modelOwner,
                model = model,
                notation = notation,
                createdAt = now,
                updatedAt = now
            )
        )
        componentsRepository.save(
            Components(
                name = "node-component",
                version = "1.0.0",
                owner = notationOwner,
                notation = notation,
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
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(
            get("/api/v1/node-types?notationId=${notation.id}&modelId=${model.id}&page=0&size=10")
                .withAuth(editor.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(nodeType.id.toString()))
    }

    @Test
    fun `list node types denies unrelated notation even with model edit access`() {
        val now = Instant.now()
        val notationOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-notation-owner-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val modelOwner = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-model-owner-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val editor = usersRepository.save(
            ru.kavader.arepos.model.Users(
                email = "node-model-editor-unrelated@test.com",
                role = Role.USER,
                createdAt = now
            )
        )
        val notation = notationsRepository.save(
            ru.kavader.arepos.model.Notations(
                name = "node-notation-unrelated",
                version = "1.0.0",
                owner = notationOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "node-model-unrelated",
                version = "1.0.0",
                owner = modelOwner,
                createdAt = now,
                updatedAt = now
            )
        )
        val nodeType = nodeTypesRepository.save(
            ru.kavader.arepos.model.NodeTypes(
                name = "node-type-unrelated",
                owner = notationOwner,
                createdAt = now
            )
        )
        componentsRepository.save(
            Components(
                name = "node-component-unrelated",
                version = "1.0.0",
                owner = notationOwner,
                notation = notation,
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
                createdAt = now,
                updatedAt = now
            )
        )

        mockMvc.perform(
            get("/api/v1/node-types?notationId=${notation.id}&modelId=${model.id}&page=0&size=10")
                .withAuth(editor.id!!, Role.USER)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
    }
}
