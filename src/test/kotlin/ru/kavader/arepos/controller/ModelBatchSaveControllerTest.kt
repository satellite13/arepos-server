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
import ru.kavader.arepos.model.*
import ru.kavader.arepos.repository.*
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ModelBatchSaveControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Autowired
    lateinit var nodeTypesRepository: NodeTypesRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var linksRepository: LinksRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Test
    fun `batch save returns 400 when operation list exceeds limit`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-limit-owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "batch-limit-model",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )

        val oversizedCreate = (1..1001).map { idx ->
            mapOf(
                "tempId" to "tmp-$idx",
                "name" to "node-$idx",
                "nodeTypeId" to UUID.randomUUID().toString(),
                "parentNodeId" to null,
                "attrs" to null
            )
        }
        val payload = mapOf(
            "nodes" to mapOf(
                "create" to oversizedCreate
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `batch save returns 409 when baseUpdatedAt mismatches node`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m1",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt1",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val baseTime = Instant.parse("2024-06-01T10:00:00Z")
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "node1",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = baseTime
            )
        )
        val nodeId = node.id!!

        val staleBase = Instant.parse("2024-06-01T09:00:00Z")
        val payload = mapOf(
            "nodes" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to nodeId.toString(),
                        "name" to "renamed",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to null,
                        "baseUpdatedAt" to staleBase.toString()
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Concurrent modification"))
            .andExpect(jsonPath("$.conflicts[0].kind").value("node"))
            .andExpect(jsonPath("$.conflicts[0].id").value(nodeId.toString()))

        val reloaded = nodesRepository.findById(nodeId).orElseThrow()
        assertEquals("node1", reloaded.name)
    }

    @Test
    fun `batch save succeeds when baseUpdatedAt matches`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner2@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m2",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt2",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val baseTime = Instant.parse("2024-07-01T12:00:00Z")
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "n",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = baseTime
            )
        )
        val nodeId = node.id!!

        val payload = mapOf(
            "nodes" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to nodeId.toString(),
                        "name" to "ok-name",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to null,
                        "baseUpdatedAt" to baseTime.toString()
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)

        assertEquals("ok-name", nodesRepository.findById(nodeId).orElseThrow().name)
    }

    @Test
    fun `batch save detects duplicate node create on retry by stableId`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner-retry@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-retry",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt-retry",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val stableTempId = UUID.randomUUID().toString()
        val payload = mapOf(
            "nodes" to mapOf(
                "create" to listOf(
                    mapOf(
                        "tempId" to stableTempId,
                        "name" to "retry-node",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to null
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodesCreated").value(1))

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Concurrent modification"))
            .andExpect(jsonPath("$.conflicts[0].kind").value("node"))

        val createdNodes = nodesRepository.findByModel(model, org.springframework.data.domain.Pageable.unpaged())
        assertEquals(1, createdNodes.totalElements)
    }

    @Test
    fun `batch save skips version check when force is true`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner3@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m3",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt3",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val baseTime = Instant.parse("2024-08-01T08:00:00Z")
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "force-node",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = baseTime
            )
        )
        val nodeId = node.id!!

        val payload = mapOf(
            "force" to true,
            "nodes" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to nodeId.toString(),
                        "name" to "forced",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to null,
                        "baseUpdatedAt" to "2000-01-01T00:00:00Z"
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)

        assertEquals("forced", nodesRepository.findById(nodeId).orElseThrow().name)
    }

    @Test
    fun `batch save accepts legacy delete as uuid strings`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner4@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m4",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt4",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val node = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "to-delete",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val nodeId = node.id!!

        val payload = mapOf(
            "nodes" to mapOf(
                "delete" to listOf(nodeId.toString())
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)

        assertEquals(false, nodesRepository.existsById(nodeId))
    }

    @Test
    fun `batch save deletes a node whose incident link is still in the update list`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-incident-owner@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-incident",
                createdAt = Instant.now(),
                attrs = null,
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt-incident",
                attrs = null,
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val linkType = linkTypesRepository.save(
            LinkTypes(
                name = "lt-incident",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val source = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "source",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val target = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "target",
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = null,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val link = linksRepository.save(
            Links(
                stableId = UUID.randomUUID(),
                source = source,
                target = target,
                attrs = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                owner = owner,
                linkType = linkType,
                model = model
            )
        )

        val payload = mapOf(
            "nodes" to mapOf(
                "delete" to listOf(source.id.toString())
            ),
            "links" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to link.id.toString(),
                        "sourceId" to source.id.toString(),
                        "targetId" to target.id.toString(),
                        "linkTypeId" to linkType.id.toString(),
                        "attrs" to null,
                        "baseUpdatedAt" to link.updatedAt.toString()
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)

        assertEquals(false, nodesRepository.existsById(source.id!!))
        assertEquals(true, nodesRepository.existsById(target.id!!))
        assertEquals(false, linksRepository.existsById(link.id!!))
    }

    @Test
    fun `batch save creates graph topologically and remaps diagram attrs`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner-topo@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-topo",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt-topo",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val linkType = linkTypesRepository.save(
            LinkTypes(
                name = "lt-topo-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            Notations(
                name = "notation-topo",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )

        val payload = mapOf(
            "nodes" to mapOf(
                "create" to listOf(
                    mapOf(
                        "tempId" to "temp-parent",
                        "name" to "Parent",
                        "nodeTypeId" to nodeType.id.toString(),
                        "attrs" to """{"role":"parent"}"""
                    ),
                    mapOf(
                        "tempId" to "temp-child",
                        "name" to "Child",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to "temp-parent",
                        "attrs" to """{"role":"child"}"""
                    )
                )
            ),
            "links" to mapOf(
                "create" to listOf(
                    mapOf(
                        "tempId" to "temp-link",
                        "sourceId" to "temp-parent",
                        "targetId" to "temp-child",
                        "linkTypeId" to linkType.id.toString(),
                        "attrs" to """{"kind":"contains"}"""
                    )
                )
            ),
            "diagrams" to mapOf(
                "create" to listOf(
                    mapOf(
                        "tempId" to "temp-diagram",
                        "name" to "Diagram",
                        "version" to "1.0.0",
                        "notationId" to notation.id.toString(),
                        "attrs" to """
                            {
                              "instances": {
                                "nodes": [
                                  {"id":"n1","modelNodeId":"temp-parent"},
                                  {"id":"n2","modelNodeId":"temp-child"}
                                ],
                                "edges": [
                                  {
                                    "id":"e1",
                                    "modelLinkId":"temp-link",
                                    "sourceModelNodeId":"temp-parent",
                                    "targetModelNodeId":"temp-child",
                                    "sourceInstanceId":"n1",
                                    "targetInstanceId":"n2"
                                  }
                                ]
                              }
                            }
                        """.trimIndent()
                    )
                )
            )
        )

        val mvcResult = mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodesCreated").value(2))
            .andExpect(jsonPath("$.linksCreated").value(1))
            .andExpect(jsonPath("$.diagramsCreated").value(1))
            .andReturn()

        val body = objectMapper.readTree(mvcResult.response.contentAsString)
        val parentId = UUID.fromString(body.path("nodeIdMap").path("temp-parent").asText())
        val childId = UUID.fromString(body.path("nodeIdMap").path("temp-child").asText())
        val linkId = UUID.fromString(body.path("linkIdMap").path("temp-link").asText())
        val diagramId = UUID.fromString(body.path("diagramIdMap").path("temp-diagram").asText())

        val childNode = nodesRepository.findById(childId).orElseThrow()
        assertEquals(parentId, childNode.parentNode?.id)

        val link = linksRepository.findById(linkId).orElseThrow()
        assertEquals(parentId, link.source.id)
        assertEquals(childId, link.target.id)

        val diagram = diagramsRepository.findById(diagramId).orElseThrow()
        val attrs = diagram.attrs ?: error("Diagram attrs must be set")
        assertTrue(attrs.contains(parentId.toString()))
        assertTrue(attrs.contains(childId.toString()))
        assertTrue(attrs.contains(linkId.toString()))
        assertTrue(!attrs.contains("temp-parent"))
        assertTrue(!attrs.contains("temp-child"))
        assertTrue(!attrs.contains("temp-link"))
    }

    @Test
    fun `batch save moves existing node under newly created folder in same request`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner-move-under-new@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-move-under-new",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt-move-under-new",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val existing = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Existing",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val folderTempId = UUID.randomUUID().toString()
        val payload = mapOf(
            "nodes" to mapOf(
                "create" to listOf(
                    mapOf(
                        "tempId" to folderTempId,
                        "name" to "New folder",
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to null,
                        "attrs" to """{"role":"folder"}"""
                    )
                ),
                "update" to listOf(
                    mapOf(
                        "id" to existing.id.toString(),
                        "name" to existing.name,
                        "nodeTypeId" to nodeType.id.toString(),
                        "parentNodeId" to folderTempId,
                        "attrs" to existing.attrs,
                        "baseUpdatedAt" to existing.updatedAt.toString()
                    )
                )
            )
        )

        val mvcResult = mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodesCreated").value(1))
            .andExpect(jsonPath("$.nodesUpdated").value(1))
            .andReturn()

        val body = objectMapper.readTree(mvcResult.response.contentAsString)
        val folderId = UUID.fromString(body.path("nodeIdMap").path(folderTempId).asText())
        val moved = nodesRepository.findById(requireNotNull(existing.id)).orElseThrow()
        assertEquals(folderId, moved.parentNode?.id)
    }

    @Test
    fun `batch save cleans diagram attrs after delete and bumps model sync revision`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner-cleanup@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-cleanup",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner,
                syncRevision = 0
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt-cleanup",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            Notations(
                name = "notation-cleanup",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        val deletedNode = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "to-delete",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val keptNode = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "to-keep",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val attrsWithDeletedNode = """
            {
              "instances": {
                "nodes": [
                  {"id":"inst-deleted","modelNodeId":"${deletedNode.id}"},
                  {"id":"inst-kept","modelNodeId":"${keptNode.id}"}
                ],
                "edges": [
                  {"id":"edge1","sourceInstanceId":"inst-deleted","targetInstanceId":"inst-kept"}
                ]
              }
            }
        """.trimIndent()
        val diagram = diagramsRepository.save(
            Diagrams(
                name = "Cleanup diagram",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                attrs = attrsWithDeletedNode,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        val payload = mapOf(
            "nodes" to mapOf(
                "delete" to listOf(deletedNode.id.toString())
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodesDeleted").value(1))

        assertTrue(!nodesRepository.existsById(requireNotNull(deletedNode.id)))

        val updatedDiagram = diagramsRepository.findById(requireNotNull(diagram.id)).orElseThrow()
        val updatedAttrs = updatedDiagram.attrs ?: error("Diagram attrs should stay non-null")
        assertTrue(!updatedAttrs.contains(requireNotNull(deletedNode.id).toString()))
        assertTrue(updatedAttrs.contains(requireNotNull(keptNode.id).toString()))
        assertTrue(!updatedAttrs.contains("edge1"))

        val reloadedModel = modelsRepository.findById(requireNotNull(model.id)).orElseThrow()
        assertNotNull(reloadedModel.syncRevision)
        assertTrue(reloadedModel.syncRevision > 0)
    }

    @Test
    fun `batch save move diagram with null attrs keeps stored canvas`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner-move-diagram@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-move-diagram",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val nodeType = nodeTypesRepository.save(
            NodeTypes(
                name = "nt-move-diagram-dir",
                createdAt = Instant.now(),
                owner = owner
            )
        )
        val folder = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Folder",
                model = model,
                owner = owner,
                nodeType = nodeType,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val notation = notationsRepository.save(
            Notations(
                name = "notation-move-diagram",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        val canvas = """{"instances":{"nodes":[{"id":"i1","modelNodeId":"${folder.id}"}],"edges":[]}}"""
        val diagram = diagramsRepository.save(
            Diagrams(
                name = "Imported diagram",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                attrs = canvas,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        val payload = mapOf(
            "diagrams" to mapOf(
                "update" to listOf(
                    mapOf(
                        "id" to diagram.id.toString(),
                        "name" to diagram.name,
                        "version" to diagram.version,
                        "notationId" to notation.id.toString(),
                        "nodeId" to folder.id.toString(),
                        "attrs" to null,
                        "baseUpdatedAt" to diagram.updatedAt.toString()
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.diagramsUpdated").value(1))

        val updated = diagramsRepository.findById(requireNotNull(diagram.id)).orElseThrow()
        assertEquals(folder.id, updated.node?.id)
        val keptAttrs = updated.attrs ?: error("Diagram attrs must stay set")
        assertTrue(keptAttrs.contains("i1"))
        assertTrue(keptAttrs.contains(requireNotNull(folder.id).toString()))
    }

    @Test
    fun `batch save create returns 409 when diagram name and version already exist`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner-diagram-dup@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-diagram-dup",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            Notations(
                name = "notation-diagram-dup",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        diagramsRepository.save(
            Diagrams(
                name = "Sales Price Calculator",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                attrs = """{"instances":{"nodes":[],"edges":[]}}""",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        val payload = mapOf(
            "diagrams" to mapOf(
                "create" to listOf(
                    mapOf(
                        "tempId" to "temp-diagram",
                        "name" to "Sales Price Calculator",
                        "version" to "1.0.0",
                        "notationId" to notation.id.toString(),
                        "attrs" to """{"instances":{"nodes":[],"edges":[]}}"""
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("DIAGRAM_NAME_VERSION_EXISTS"))
            .andExpect(
                jsonPath("$.message").value(
                    "Diagram with name 'Sales Price Calculator' and version '1.0.0' already exists"
                )
            )
            .andExpect(jsonPath("$.name").value("Sales Price Calculator"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.suggestedVersion").value("1.1.0"))
    }

    @Test
    fun `batch save create returns 409 when a deleted diagram occupies the same name and version`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner-diagram-deleted-dup@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-diagram-deleted-dup",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            Notations(
                name = "notation-diagram-deleted-dup",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        val deleted = diagramsRepository.save(
            Diagrams(
                name = "Sales Price Calculator",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                deleted = true,
                attrs = """{"instances":{"nodes":[],"edges":[]}}""",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        val payload = mapOf(
            "diagrams" to mapOf(
                "create" to listOf(
                    mapOf(
                        "tempId" to "temp-diagram",
                        "name" to "Sales Price Calculator",
                        "version" to "1.0.0",
                        "notationId" to notation.id.toString(),
                        "attrs" to """{"instances":{"nodes":[],"edges":[]}}"""
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("DIAGRAM_NAME_VERSION_IN_TRASH"))
            .andExpect(jsonPath("$.name").value("Sales Price Calculator"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.deletedDiagramId").value(deleted.id.toString()))
            .andExpect(jsonPath("$.suggestedVersion").value("1.1.0"))
    }

    @Test
    fun `batch save create replaces a deleted diagram when replaceDeletedId is sent`() {
        val owner = usersRepository.save(
            Users(
                email = "batch-owner-diagram-replace@test.com",
                role = Role.ADMIN,
                createdAt = Instant.now()
            )
        )
        val model = modelsRepository.save(
            Models(
                name = "m-diagram-replace",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )
        val notation = notationsRepository.save(
            Notations(
                name = "notation-diagram-replace",
                version = "1.0.0",
                owner = owner,
                createdAt = Instant.now()
            )
        )
        val deleted = diagramsRepository.save(
            Diagrams(
                name = "Sales Price Calculator",
                version = "1.0.0",
                owner = owner,
                model = model,
                notation = notation,
                deleted = true,
                attrs = """{"instances":{"nodes":[],"edges":[]}}""",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        val payload = mapOf(
            "diagrams" to mapOf(
                "create" to listOf(
                    mapOf(
                        "tempId" to "temp-diagram",
                        "name" to "Sales Price Calculator",
                        "version" to "1.0.0",
                        "notationId" to notation.id.toString(),
                        "attrs" to """{"instances":{"nodes":[{"id":"n1"}]}}""",
                        "replaceDeletedId" to deleted.id.toString()
                    )
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/batch-save")
                .withAuth(owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.diagramsCreated").value(1))

        assertTrue(diagramsRepository.findById(requireNotNull(deleted.id)).isEmpty)
        val created = diagramsRepository.findByModelAndNameAndVersion(model, "Sales Price Calculator", "1.0.0")
        assertNotNull(created)
        assertEquals(false, created.deleted)
        assertTrue(created.attrs!!.contains("n1"))
    }
}
