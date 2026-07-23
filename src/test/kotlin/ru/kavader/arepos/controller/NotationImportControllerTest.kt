package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.import.ImportedComponent
import ru.kavader.arepos.dto.import.ImportedLinkType
import ru.kavader.arepos.dto.import.ImportedNodeShape
import ru.kavader.arepos.dto.import.ImportedNodeType
import ru.kavader.arepos.dto.import.NotationImportMeta
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.NodeShapes
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.ComponentsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.NodeShapesRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
class NotationImportControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var nodeShapesRepository: NodeShapesRepository

    @Autowired
    lateinit var componentsRepository: ComponentsRepository

    @Test
    fun `import creates notation owned by caller`() {
        val caller = persistUser("notation-importer@test.com")
        val request = NotationImportRequest(
            notation = NotationImportMeta(
                name = "Imported notation",
                version = "2.1.0",
                attrs = """{"source":"test"}"""
            )
        )

        val notationId = mockMvc.perform(
            post("/api/v1/notations/import")
                .withAuth(caller.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.notationId").isNotEmpty)
            .andReturn()
            .response
            .let { objectMapper.readTree(it.contentAsString).path("notationId").asText() }

        val notation = notationsRepository.findById(java.util.UUID.fromString(notationId)).orElseThrow()
        assertEquals(caller.id, notation.owner.id)
        assertEquals("Imported notation", notation.name)
        assertEquals("2.1.0", notation.version)
    }

    @Test
    fun `import reuses existing node and link types by name for same owner`() {
        val caller = persistUser("notation-reuse@test.com")
        val existingNodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "Existing Node",
                owner = caller,
                createdAt = Instant.now()
            )
        )
        val existingLinkType = linkTypesRepository.save(
            LinkTypes(
                name = "Existing Link",
                owner = caller,
                createdAt = Instant.now()
            )
        )
        val request = NotationImportRequest(
            notation = NotationImportMeta(name = "Reuse types notation", version = "1.0.0"),
            nodeTypes = listOf(
                ImportedNodeType(id = "node-type-source", name = "existing node")
            ),
            linkTypes = listOf(
                ImportedLinkType(id = "link-type-source", name = "EXISTING LINK")
            )
        )

        mockMvc.perform(
            post("/api/v1/notations/import")
                .withAuth(caller.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(
                jsonPath("$.nodeTypeIdMap['node-type-source']")
                    .value(existingNodeType.id.toString())
            )
            .andExpect(
                jsonPath("$.linkTypeIdMap['link-type-source']")
                    .value(existingLinkType.id.toString())
            )

        assertEquals(1, nodeTypesRepository.count())
        assertEquals(1, linkTypesRepository.count())
    }

    @Test
    fun `import creates own types when another owner already has the same names`() {
        val otherOwner = persistUser("notation-other-owner@test.com")
        val otherNodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "Application Function",
                owner = otherOwner,
                createdAt = Instant.now()
            )
        )
        val otherLinkType = linkTypesRepository.save(
            LinkTypes(
                name = "Serving",
                owner = otherOwner,
                createdAt = Instant.now()
            )
        )
        val caller = persistUser("notation-import-own@test.com")
        val request = NotationImportRequest(
            notation = NotationImportMeta(name = "Own catalog notation", version = "1.0.0"),
            nodeTypes = listOf(
                ImportedNodeType(id = "node-type-source", name = "Application Function")
            ),
            linkTypes = listOf(
                ImportedLinkType(id = "link-type-source", name = "Serving")
            )
        )

        val response = mockMvc.perform(
            post("/api/v1/notations/import")
                .withAuth(caller.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.nodeTypeIdMap['node-type-source']").isNotEmpty)
            .andExpect(jsonPath("$.linkTypeIdMap['link-type-source']").isNotEmpty)
            .andReturn()
            .response
            .let { objectMapper.readTree(it.contentAsString) }

        val createdNodeTypeId = java.util.UUID.fromString(response.path("nodeTypeIdMap").path("node-type-source").asText())
        val createdLinkTypeId = java.util.UUID.fromString(response.path("linkTypeIdMap").path("link-type-source").asText())
        assertNotEquals(otherNodeType.id, createdNodeTypeId)
        assertNotEquals(otherLinkType.id, createdLinkTypeId)

        val createdNodeType = nodeTypesRepository.findById(createdNodeTypeId).orElseThrow()
        val createdLinkType = linkTypesRepository.findById(createdLinkTypeId).orElseThrow()
        assertEquals(caller.id, createdNodeType.owner.id)
        assertEquals(caller.id, createdLinkType.owner.id)
        assertEquals(2, nodeTypesRepository.count())
        assertEquals(2, linkTypesRepository.count())
    }

    @Test
    fun `import with shapes creates rows and remaps attrs`() {
        val caller = persistUser("notation-shapes-import@test.com")
        val oldShapeId = "shape-source-hex"
        val outline = """[{"type":"M","x":0,"y":0}]"""
        val request = NotationImportRequest(
            notation = NotationImportMeta(name = "Shapes import notation", version = "1.0.0"),
            nodeTypes = listOf(
                ImportedNodeType(id = "node-type-1", name = "Hex Node")
            ),
            shapes = listOf(
                ImportedNodeShape(id = oldShapeId, name = "Hex", outline = outline)
            ),
            components = listOf(
                ImportedComponent(
                    id = "comp-1",
                    name = "Hex Component",
                    nodeTypeId = "node-type-1",
                    attrs = """{"diagramStyle":{"customShapeId":"$oldShapeId"}}"""
                )
            )
        )

        val response = mockMvc.perform(
            post("/api/v1/notations/import")
                .withAuth(caller.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.shapeIdMap['$oldShapeId']").isNotEmpty)
            .andReturn()
            .response
            .let { objectMapper.readTree(it.contentAsString) }

        val newShapeId = UUID.fromString(response.path("shapeIdMap").path(oldShapeId).asText())
        assertNotEquals(oldShapeId, newShapeId.toString())

        val savedShape = nodeShapesRepository.findById(newShapeId).orElseThrow()
        assertEquals(caller.id, savedShape.owner.id)
        assertEquals("Hex", savedShape.name)
        assertEquals(objectMapper.readTree(outline), objectMapper.readTree(savedShape.outline))

        val notationId = UUID.fromString(response.path("notationId").asText())
        val notation = notationsRepository.findById(notationId).orElseThrow()
        val componentId = UUID.fromString(response.path("componentIdMap").path("comp-1").asText())
        val component = componentsRepository.findById(componentId).orElseThrow()
        assertEquals(notation.id, component.notation.id)

        val remappedShapeId = objectMapper.readTree(component.attrs)
            .path("diagramStyle")
            .path("customShapeId")
            .asText()
        assertEquals(newShapeId.toString(), remappedShapeId)
    }

    @Test
    fun `import renames shape on name conflict`() {
        val caller = persistUser("notation-shape-rename@test.com")
        nodeShapesRepository.save(
            NodeShapes(
                name = "Hex",
                owner = caller,
                outline = """[{"type":"M","x":0,"y":0}]""",
                createdAt = Instant.now()
            )
        )
        val importedShapeId = "shape-source-conflict"
        val request = NotationImportRequest(
            notation = NotationImportMeta(name = "Shape rename notation", version = "1.0.0"),
            shapes = listOf(
                ImportedNodeShape(
                    id = importedShapeId,
                    name = "Hex",
                    outline = """[{"type":"M","x":1,"y":1}]"""
                )
            )
        )

        val response = mockMvc.perform(
            post("/api/v1/notations/import")
                .withAuth(caller.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.shapeIdMap['$importedShapeId']").isNotEmpty)
            .andReturn()
            .response
            .let { objectMapper.readTree(it.contentAsString) }

        val importedShape = nodeShapesRepository.findById(
            UUID.fromString(response.path("shapeIdMap").path(importedShapeId).asText())
        ).orElseThrow()
        assertEquals("Hex (2)", importedShape.name)
        assertEquals(caller.id, importedShape.owner.id)
    }

    @Test
    fun `import synthesizes shape from component outline when shapes empty`() {
        val caller = persistUser("notation-shape-fallback@test.com")
        val oldShapeId = "fallback-shape-id"
        val outline = """[{"type":"M","x":0,"y":0}]"""
        val request = NotationImportRequest(
            notation = NotationImportMeta(name = "Shape fallback notation", version = "1.0.0"),
            nodeTypes = listOf(
                ImportedNodeType(id = "node-type-1", name = "Fallback Node")
            ),
            components = listOf(
                ImportedComponent(
                    id = "comp-1",
                    name = "Fallback Component",
                    nodeTypeId = "node-type-1",
                    attrs = """{"diagramStyle":{"customShapeId":"$oldShapeId","customOutline":$outline}}"""
                )
            )
        )

        val response = mockMvc.perform(
            post("/api/v1/notations/import")
                .withAuth(caller.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.shapeIdMap['$oldShapeId']").isNotEmpty)
            .andReturn()
            .response
            .let { objectMapper.readTree(it.contentAsString) }

        val newShapeId = UUID.fromString(response.path("shapeIdMap").path(oldShapeId).asText())
        val savedShape = nodeShapesRepository.findById(newShapeId).orElseThrow()
        assertEquals(caller.id, savedShape.owner.id)
        assertEquals("Imported shape", savedShape.name)
        assertEquals(objectMapper.readTree(outline), objectMapper.readTree(savedShape.outline))

        val componentId = UUID.fromString(response.path("componentIdMap").path("comp-1").asText())
        val component = componentsRepository.findById(componentId).orElseThrow()
        val remappedShapeId = objectMapper.readTree(component.attrs)
            .path("diagramStyle")
            .path("customShapeId")
            .asText()
        assertEquals(newShapeId.toString(), remappedShapeId)
    }

    private fun persistUser(email: String): Users =
        usersRepository.save(
            Users(
                email = email,
                role = Role.USER,
                createdAt = Instant.now()
            )
        )
}
