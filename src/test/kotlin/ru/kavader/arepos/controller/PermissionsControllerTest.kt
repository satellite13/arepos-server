package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Files
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.FilesRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class PermissionsControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var nodeShapesRepository: NodeShapesRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var filesRepository: FilesRepository

    private lateinit var owner: Users
    private lateinit var outsider: Users
    private lateinit var admin: Users
    private lateinit var model: Models
    private lateinit var shape: NodeShapes
    private lateinit var nodeType: NodeTypes
    private lateinit var linkType: LinkTypes
    private lateinit var file: Files

    @BeforeEach
    fun setUp() {
        owner = usersRepository.save(
            Users(
                email = "permissions-owner-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        outsider = usersRepository.save(
            Users(
                email = "permissions-outsider-${UUID.randomUUID()}@test.com",
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
        admin = usersRepository.save(
            Users(
                email = "permissions-admin-${UUID.randomUUID()}@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        model = modelsRepository.save(
            Models(
                name = "permissions-model-${UUID.randomUUID()}",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now(),
                deleted = false
            )
        )
        shape = nodeShapesRepository.save(
            NodeShapes(
                name = "permissions-shape-${UUID.randomUUID()}",
                owner = owner,
                outline = """[{"type":"M","x":0,"y":0}]""",
                createdAt = Instant.now()
            )
        )
        nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "permissions-node-type-${UUID.randomUUID()}",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        linkType = linkTypesRepository.save(
            LinkTypes(
                name = "permissions-link-type-${UUID.randomUUID()}",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        file = filesRepository.save(
            Files(
                id = UUID.randomUUID(),
                owner = owner,
                filename = "permissions-file-${UUID.randomUUID()}.txt",
                contentType = "text/plain",
                size = 12L,
                objectKey = "permissions/file-${UUID.randomUUID()}.txt",
                createdAt = Instant.now()
            )
        )
    }

    @Test
    fun `returns true for owner model edit`() {
        val payload = mapOf(
            "resourceType" to "MODEL",
            "resourceId" to model.id.toString(),
            "actions" to listOf("EDIT")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resourceType").value("MODEL"))
            .andExpect(jsonPath("$.resourceId").value(model.id.toString()))
            .andExpect(jsonPath("$.decisions.EDIT").value(true))
    }

    @Test
    fun `returns false for outsider model edit`() {
        val payload = mapOf(
            "resourceType" to "MODEL",
            "resourceId" to model.id.toString(),
            "actions" to listOf("EDIT")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(outsider.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions.EDIT").value(false))
    }

    @Test
    fun `returns true for owner node-shape manage`() {
        val payload = mapOf(
            "resourceType" to "NODE_SHAPE",
            "resourceId" to shape.id.toString(),
            "actions" to listOf("MANAGE")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resourceType").value("NODE_SHAPE"))
            .andExpect(jsonPath("$.decisions.MANAGE").value(true))
    }

    @Test
    fun `returns false for outsider node-shape view`() {
        val payload = mapOf(
            "resourceType" to "NODE_SHAPE",
            "resourceId" to shape.id.toString(),
            "actions" to listOf("VIEW")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(outsider.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions.VIEW").value(false))
    }

    @Test
    fun `returns true for owner file view`() {
        val payload = mapOf(
            "resourceType" to "FILE",
            "resourceId" to file.id.toString(),
            "actions" to listOf("VIEW")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions.VIEW").value(true))
    }

    @Test
    fun `returns false for outsider file view`() {
        val payload = mapOf(
            "resourceType" to "FILE",
            "resourceId" to file.id.toString(),
            "actions" to listOf("VIEW")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(outsider.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions.VIEW").value(false))
    }

    @Test
    fun `returns true for owner node-type edit`() {
        val payload = mapOf(
            "resourceType" to "NODE_TYPE",
            "resourceId" to nodeType.id.toString(),
            "actions" to listOf("EDIT")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions.EDIT").value(true))
    }

    @Test
    fun `returns false for outsider link-type edit`() {
        val payload = mapOf(
            "resourceType" to "LINK_TYPE",
            "resourceId" to linkType.id.toString(),
            "actions" to listOf("EDIT")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(outsider.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions.EDIT").value(false))
    }

    @Test
    fun `returns true for admin panel view when role is admin`() {
        val payload = mapOf(
            "resourceType" to "ADMIN_PANEL",
            "resourceId" to admin.id.toString(),
            "actions" to listOf("VIEW")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(admin.id!!, Role.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions.VIEW").value(true))
    }

    @Test
    fun `returns false for admin panel view when role is user`() {
        val payload = mapOf(
            "resourceType" to "ADMIN_PANEL",
            "resourceId" to owner.id.toString(),
            "actions" to listOf("VIEW")
        )

        mockMvc.perform(
            post("/api/v1/permissions/check")
                .withAuth(owner.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions.VIEW").value(false))
    }
}
