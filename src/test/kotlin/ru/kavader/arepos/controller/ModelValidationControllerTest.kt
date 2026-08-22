package ru.kavader.arepos.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.kavader.arepos.dto.system.ModelSyncEntityEvent
import ru.kavader.arepos.model.DiagramEditLocks
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramEditLocksRepository
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.ResourceSharesRepository
import ru.kavader.arepos.repository.UsersRepository
import ru.kavader.arepos.security.DIAGRAM_LOCK_HELD_BY_ANOTHER_USER
import ru.kavader.arepos.service.ModelSyncBroadcaster
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ModelValidationControllerTest : ControllerIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

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
    lateinit var resourceSharesRepository: ResourceSharesRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var diagramEditLocksRepository: DiagramEditLocksRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoSpyBean
    lateinit var modelSyncBroadcaster: ModelSyncBroadcaster

    private lateinit var owner: Users
    private lateinit var model: Models
    private lateinit var directoryType: NodeTypes
    private lateinit var applicationComponentType: NodeTypes
    private lateinit var servingType: LinkTypes
    private lateinit var notation: Notations
    private var diagramVersionCounter = 0

    @BeforeEach
    fun setUp() {
        owner = saveUser(Role.USER)
        model = saveModel(owner)
        directoryType = saveNodeType(owner, "Directory")
        applicationComponentType = saveNodeType(owner, "Application Component")
        servingType = saveLinkType(owner, "Serving")
        notation = saveNotation(owner)
        diagramVersionCounter = 0
    }

    @Test
    fun `groups nodes by type and case-insensitive trimmed name and skips directory`() {
        val apps = saveNode(model, "Apps", directoryType)
        saveNode(model, " apps ", directoryType)
        saveNode(model, "CRM", applicationComponentType, parent = apps)
        saveNode(model, " crm ", applicationComponentType, parent = apps)
        saveNode(model, "Other", applicationComponentType, parent = apps)

        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(owner.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicateNodes.length()").value(1))
            .andExpect(jsonPath("$.duplicateNodes[0].count").value(2))
            .andExpect(jsonPath("$.duplicateNodes[0].nodes[0].parentName").value("Apps"))
    }

    @Test
    fun `groups directed links and ignores reverse pair`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        saveLink(model, source, target, servingType)
        saveLink(model, source, target, servingType)
        saveLink(model, target, source, servingType)

        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(owner.id!!, Role.USER))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicateLinks.length()").value(1))
            .andExpect(jsonPath("$.duplicateLinks[0].count").value(2))
    }

    @Test
    fun `forbidden viewer gets 403`() {
        val viewer = saveUser(Role.USER)
        val stranger = saveUser(Role.USER)
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(owner.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(viewer.id!!, Role.USER))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/models/${model.id}/validation-report").withAuth(stranger.id!!, Role.USER))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `nodes preview splits unique matching and AB links and reads typeProperties only`() {
        val keep = saveNode(
            model,
            "CRM",
            applicationComponentType,
            attrs = """{"typeProperties":{"owner":"a"},"notationComponents":{"x":1}}"""
        )
        val drop = saveNode(
            model,
            "CRM",
            applicationComponentType,
            attrs = """{"typeProperties":{"owner":"b"},"notationComponents":{"x":1},"documentFileId":"11111111-1111-1111-1111-111111111111"}"""
        )
        val nodeX = saveNode(model, "X", applicationComponentType)
        val nodeY = saveNode(model, "Y", applicationComponentType)
        saveLink(model, drop, nodeX, servingType)
        saveLink(model, keep, nodeY, servingType)
        saveLink(model, drop, nodeY, servingType)
        saveLink(model, drop, keep, servingType)

        mockMvc.perform(
            get("/api/v1/models/${model.id}/validation/merge-nodes-preview")
                .param("keepId", keep.id.toString())
                .param("dropId", drop.id.toString())
                .withAuth(owner.id!!, Role.USER)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.keepTypeProperties.owner").value("a"))
            .andExpect(jsonPath("$.dropTypeProperties.owner").value("b"))
            .andExpect(jsonPath("$.dropTypeProperties.documentFileId").doesNotExist())
            .andExpect(jsonPath("$.dropTypeProperties.notationComponents").doesNotExist())
            .andExpect(jsonPath("$.uniqueLinks.length()").value(1))
            .andExpect(jsonPath("$.uniqueLinks[0].otherNodeName").value("X"))
            .andExpect(jsonPath("$.uniqueLinks[0].direction").value("out"))
            .andExpect(jsonPath("$.linksToDelete.length()").value(2))
            .andExpect(jsonPath("$.hasDocuments").value(true))
            .andExpect(jsonPath("$.hasChildren").value(false))
            .andExpect(jsonPath("$.keepUpdatedAt").exists())
            .andExpect(jsonPath("$.dropUpdatedAt").exists())
    }

    @Test
    fun `preview same id or non-duplicate returns 400`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val other = saveNode(model, "Billing", applicationComponentType)

        mockMvc.perform(
            get("/api/v1/models/${model.id}/validation/merge-nodes-preview")
                .param("keepId", keep.id.toString())
                .param("dropId", keep.id.toString())
                .withAuth(owner.id!!, Role.USER)
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/v1/models/${model.id}/validation/merge-nodes-preview")
                .param("keepId", keep.id.toString())
                .param("dropId", other.id.toString())
                .withAuth(owner.id!!, Role.USER)
        ).andExpect(status().isBadRequest)

        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        val link = saveLink(model, source, target, servingType)

        mockMvc.perform(
            get("/api/v1/models/${model.id}/validation/merge-links-preview")
                .param("keepId", link.id.toString())
                .param("dropId", link.id.toString())
                .withAuth(owner.id!!, Role.USER)
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `links preview returns typeProperties and diagrams`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        val keep = saveLink(
            model,
            source,
            target,
            servingType,
            attrs = """{"typeProperties":{"owner":"a"},"notationRelations":{"x":1}}"""
        )
        val drop = saveLink(
            model,
            source,
            target,
            servingType,
            attrs = """{"typeProperties":{"owner":"b"},"notationRelations":{"x":1}}"""
        )
        val keepDiagram = saveDiagram(model, "keep-edge", attrsForLink(keep.id!!))
        val dropDiagram = saveDiagram(model, "drop-edge", attrsForLink(drop.id!!))

        mockMvc.perform(
            get("/api/v1/models/${model.id}/validation/merge-links-preview")
                .param("keepId", keep.id.toString())
                .param("dropId", drop.id.toString())
                .withAuth(owner.id!!, Role.USER)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.keepTypeProperties.owner").value("a"))
            .andExpect(jsonPath("$.dropTypeProperties.owner").value("b"))
            .andExpect(jsonPath("$.keepTypeProperties.notationRelations").doesNotExist())
            .andExpect(jsonPath("$.keepUpdatedAt").exists())
            .andExpect(jsonPath("$.dropUpdatedAt").exists())
            .andExpect(jsonPath("$.keepDiagrams.length()").value(1))
            .andExpect(jsonPath("$.keepDiagrams[0].diagramId").value(keepDiagram.id.toString()))
            .andExpect(jsonPath("$.keepDiagrams[0].diagramName").value("keep-edge"))
            .andExpect(jsonPath("$.dropDiagrams.length()").value(1))
            .andExpect(jsonPath("$.dropDiagrams[0].diagramId").value(dropDiagram.id.toString()))
            .andExpect(jsonPath("$.dropDiagrams[0].diagramName").value("drop-edge"))
    }

    @Test
    fun `merge transfers unique drop link onto keep and keeps the same id`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        val nodeX = saveNode(model, "X", applicationComponentType)
        val unique = saveLink(model, drop, nodeX, servingType)

        postMerge(keep, drop, transferLinkIds = listOf(unique.id!!))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.keepId").value(keep.id.toString()))
            .andExpect(jsonPath("$.dropId").value(drop.id.toString()))

        val transferred = linksRepository.findById(unique.id!!).orElseThrow()
        assertEquals(keep.id, transferred.source.id)
        assertEquals(nodeX.id, transferred.target.id)
    }

    @Test
    fun `merge deletes matching and AB links and removes their diagram edges`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        val nodeY = saveNode(model, "Y", applicationComponentType)
        val matchingDrop = saveLink(model, drop, nodeY, servingType)
        saveLink(model, keep, nodeY, servingType)
        val ab = saveLink(model, drop, keep, servingType)
        val diagram = saveDiagram(
            model,
            "edges",
            """{"instances":{"nodes":[{"id":"i1","modelNodeId":"${drop.id}"},{"id":"i2","modelNodeId":"${keep.id}"}],"edges":[{"id":"e1","modelLinkId":"${matchingDrop.id}","sourceInstanceId":"i1","targetInstanceId":"i2"},{"id":"e2","modelLinkId":"${ab.id}","sourceInstanceId":"i1","targetInstanceId":"i2"}]}}"""
        )

        postMerge(keep, drop).andExpect(status().isOk)

        assertFalse(linksRepository.existsById(matchingDrop.id!!))
        assertFalse(linksRepository.existsById(ab.id!!))
        val attrs = diagramsRepository.findById(diagram.id!!).orElseThrow().attrs!!
        assertFalse(attrs.contains(matchingDrop.id.toString()))
        assertFalse(attrs.contains(ab.id.toString()))
    }

    @Test
    fun `merge remaps drop instances to keep and leaves both figures`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        val diagram = saveDiagram(
            model,
            "two-figures",
            """{"instances":{"nodes":[{"id":"i1","modelNodeId":"${drop.id}"},{"id":"i2","modelNodeId":"${keep.id}"}],"edges":[]}}"""
        )

        postMerge(keep, drop).andExpect(status().isOk)

        val root = objectMapper.readTree(diagramsRepository.findById(diagram.id!!).orElseThrow().attrs)
        val nodes = root.path("instances").path("nodes")
        assertEquals(2, nodes.size())
        assertEquals(keep.id.toString(), nodes[0].path("modelNodeId").asText())
        assertEquals(keep.id.toString(), nodes[1].path("modelNodeId").asText())
    }

    @Test
    fun `merge reparents diagrams whose node is drop onto keep`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        val diagram = saveDiagram(model, "owned-by-drop", """{"instances":{"nodes":[]}}""", node = drop)

        postMerge(keep, drop).andExpect(status().isOk)

        assertEquals(keep.id, diagramsRepository.findById(diagram.id!!).orElseThrow().node?.id)
    }

    @Test
    fun `merge writes typeProperties and leaves other keep attrs intact`() {
        val keep = saveNode(
            model,
            "CRM",
            applicationComponentType,
            attrs = """{"typeProperties":{"owner":"a"},"notationComponents":{"x":1},"documentFileId":"11111111-1111-1111-1111-111111111111"}"""
        )
        val drop = saveNode(model, "CRM", applicationComponentType)

        postMerge(keep, drop, typeProperties = mapOf("owner" to "merged")).andExpect(status().isOk)

        val attrs = objectMapper.readTree(nodesRepository.findById(keep.id!!).orElseThrow().attrs)
        assertEquals("merged", attrs.path("typeProperties").path("owner").asText())
        assertEquals(1, attrs.path("notationComponents").path("x").asInt())
        assertEquals("11111111-1111-1111-1111-111111111111", attrs.path("documentFileId").asText())
    }

    @Test
    fun `merge rejects drop that still has children`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        saveNode(model, "child", applicationComponentType, parent = drop)

        postMerge(keep, drop).andExpect(status().isBadRequest)
    }

    @Test
    fun `merge rejects stale keepUpdatedAt`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)

        postMerge(keep, drop, keepUpdatedAt = Instant.parse("2020-01-01T00:00:00Z"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `merge rejects transferLinkIds that are not unique`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        val nodeY = saveNode(model, "Y", applicationComponentType)
        saveLink(model, keep, nodeY, servingType)
        val matching = saveLink(model, drop, nodeY, servingType)

        postMerge(keep, drop, transferLinkIds = listOf(matching.id!!))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `merge broadcasts validation_merge_nodes with node link and diagram events`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        val nodeX = saveNode(model, "X", applicationComponentType)
        val nodeY = saveNode(model, "Y", applicationComponentType)
        val unique = saveLink(model, drop, nodeX, servingType)
        saveLink(model, keep, nodeY, servingType)
        val matching = saveLink(model, drop, nodeY, servingType)
        saveDiagram(
            model,
            "canvas",
            """{"instances":{"nodes":[{"id":"i1","modelNodeId":"${drop.id}"},{"id":"i2","modelNodeId":"${keep.id}"}],"edges":[{"id":"e1","modelLinkId":"${matching.id}","sourceInstanceId":"i1","targetInstanceId":"i2"}]}}"""
        )

        postMerge(keep, drop, transferLinkIds = listOf(unique.id!!)).andExpect(status().isOk)

        val invocation = mockingDetails(modelSyncBroadcaster).invocations
            .single { it.method.name == "broadcastModelChanged" }
        assertEquals(model.id, invocation.arguments[0])
        assertEquals("validation_merge_nodes", invocation.arguments[1])
        @Suppress("UNCHECKED_CAST")
        val types = (invocation.arguments[2] as List<ModelSyncEntityEvent>).map { it.type }.toSet()
        assertTrue(types.containsAll(listOf("node_updated", "node_deleted", "link_updated", "link_deleted", "diagram_updated")))
    }

    @Test
    fun `merge rejects lock held by another user on an affected diagram`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        val diagram = saveDiagram(
            model,
            "locked",
            """{"instances":{"nodes":[{"id":"i1","modelNodeId":"${drop.id}"}]}}"""
        )
        val other = saveUser(Role.USER)
        val now = Instant.now()
        diagramEditLocksRepository.save(
            DiagramEditLocks(
                diagram = diagram,
                lockedBy = other,
                lockedAt = now,
                lastHeartbeatAt = now,
                expiresAt = now.plusSeconds(600)
            )
        )

        postMerge(keep, drop)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value(DIAGRAM_LOCK_HELD_BY_ANOTHER_USER))
    }

    @Test
    fun `merge links remaps edges on both diagrams and keeps keep attrs besides typeProperties`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        val keep = saveLink(
            model,
            source,
            target,
            servingType,
            attrs = """{"typeProperties":{"owner":"keep"},"notationRelations":{"r":1},"relationProperties":{"p":2}}"""
        )
        val drop = saveLink(
            model,
            source,
            target,
            servingType,
            attrs = """{"typeProperties":{"owner":"drop"},"notationRelations":{"r":9}}"""
        )
        val diagramOne = saveDiagram(
            model,
            "d1",
            """{"edges":[{"id":"root1","modelLinkId":"${drop.id}"}],"instances":{"edges":[{"id":"e1","modelLinkId":"${keep.id}"},{"id":"e2","modelLinkId":"${drop.id}"}]}}"""
        )
        val diagramTwo = saveDiagram(model, "d2", attrsForLink(drop.id!!))

        postMergeLinks(keep, drop, typeProperties = mapOf("owner" to "merged"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.keepId").value(keep.id.toString()))
            .andExpect(jsonPath("$.dropId").value(drop.id.toString()))

        assertFalse(linksRepository.existsById(drop.id!!))
        val keepAttrs = objectMapper.readTree(linksRepository.findById(keep.id!!).orElseThrow().attrs)
        assertEquals("merged", keepAttrs.path("typeProperties").path("owner").asText())
        assertEquals(1, keepAttrs.path("notationRelations").path("r").asInt())
        assertEquals(2, keepAttrs.path("relationProperties").path("p").asInt())

        val d1 = objectMapper.readTree(diagramsRepository.findById(diagramOne.id!!).orElseThrow().attrs)
        val d1Edges = d1.path("instances").path("edges")
        assertEquals(2, d1Edges.size())
        assertEquals(keep.id.toString(), d1Edges[0].path("modelLinkId").asText())
        assertEquals(keep.id.toString(), d1Edges[1].path("modelLinkId").asText())
        assertEquals(keep.id.toString(), d1.path("edges")[0].path("modelLinkId").asText())

        val d2 = objectMapper.readTree(diagramsRepository.findById(diagramTwo.id!!).orElseThrow().attrs)
        assertEquals(keep.id.toString(), d2.path("instances").path("edges")[0].path("modelLinkId").asText())
    }

    @Test
    fun `merge links broadcasts validation_merge_links with link and diagram events`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        val keep = saveLink(model, source, target, servingType)
        val drop = saveLink(model, source, target, servingType)
        saveDiagram(model, "canvas", attrsForLink(drop.id!!))

        postMergeLinks(keep, drop).andExpect(status().isOk)

        val invocation = mockingDetails(modelSyncBroadcaster).invocations
            .single { it.method.name == "broadcastModelChanged" }
        assertEquals(model.id, invocation.arguments[0])
        assertEquals("validation_merge_links", invocation.arguments[1])
        @Suppress("UNCHECKED_CAST")
        val types = (invocation.arguments[2] as List<ModelSyncEntityEvent>).map { it.type }.toSet()
        assertTrue(types.containsAll(listOf("link_updated", "link_deleted", "diagram_updated")))
    }

    @Test
    fun `merge links rejects same id reverse pair or different type`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        val keep = saveLink(model, source, target, servingType)
        val reverse = saveLink(model, target, source, servingType)
        val otherType = saveLinkType(owner, "Association")
        val otherTyped = saveLink(model, source, target, otherType)

        postMergeLinks(keep, keep).andExpect(status().isBadRequest)
        postMergeLinks(keep, reverse).andExpect(status().isBadRequest)
        postMergeLinks(keep, otherTyped).andExpect(status().isBadRequest)
    }

    @Test
    fun `merge links rejects stale keepUpdatedAt`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        val keep = saveLink(model, source, target, servingType)
        val drop = saveLink(model, source, target, servingType)

        postMergeLinks(keep, drop, keepUpdatedAt = Instant.parse("2020-01-01T00:00:00Z"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `merge links rejects lock held by another user on a diagram with keep or drop edge`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        val keep = saveLink(model, source, target, servingType)
        val drop = saveLink(model, source, target, servingType)
        val diagram = saveDiagram(model, "locked", attrsForLink(keep.id!!))
        val other = saveUser(Role.USER)
        val now = Instant.now()
        diagramEditLocksRepository.save(
            DiagramEditLocks(
                diagram = diagram,
                lockedBy = other,
                lockedAt = now,
                lastHeartbeatAt = now,
                expiresAt = now.plusSeconds(600)
            )
        )

        postMergeLinks(keep, drop)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value(DIAGRAM_LOCK_HELD_BY_ANOTHER_USER))
    }

    @Test
    fun `merge links is forbidden for view-only share`() {
        val source = saveNode(model, "A", applicationComponentType)
        val target = saveNode(model, "B", applicationComponentType)
        val keep = saveLink(model, source, target, servingType)
        val drop = saveLink(model, source, target, servingType)
        val viewer = saveUser(Role.USER)
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/validation/merge-links")
                .withAuth(viewer.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mergeLinksBody(keep, drop))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `merge is forbidden for view-only share`() {
        val keep = saveNode(model, "CRM", applicationComponentType)
        val drop = saveNode(model, "CRM", applicationComponentType)
        val viewer = saveUser(Role.USER)
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = viewer,
                grantedByUser = owner,
                permission = SharePermission.VIEW,
                createdAt = Instant.now()
            )
        )

        mockMvc.perform(
            post("/api/v1/models/${model.id}/validation/merge-nodes")
                .withAuth(viewer.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mergeNodesBody(keep, drop))
        ).andExpect(status().isForbidden)
    }

    private fun postMerge(
        keep: Nodes,
        drop: Nodes,
        typeProperties: Map<String, Any?> = emptyMap(),
        transferLinkIds: List<UUID> = emptyList(),
        keepUpdatedAt: Instant = keep.updatedAt ?: keep.createdAt!!,
        dropUpdatedAt: Instant = drop.updatedAt ?: drop.createdAt!!
    ) = mockMvc.perform(
        post("/api/v1/models/${model.id}/validation/merge-nodes")
            .withAuth(owner.id!!, Role.USER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mergeNodesBody(keep, drop, typeProperties, transferLinkIds, keepUpdatedAt, dropUpdatedAt))
    )

    private fun postMergeLinks(
        keep: Links,
        drop: Links,
        typeProperties: Map<String, Any?> = emptyMap(),
        keepUpdatedAt: Instant = keep.updatedAt ?: keep.createdAt!!,
        dropUpdatedAt: Instant = drop.updatedAt ?: drop.createdAt!!
    ) = mockMvc.perform(
        post("/api/v1/models/${model.id}/validation/merge-links")
            .withAuth(owner.id!!, Role.USER)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mergeLinksBody(keep, drop, typeProperties, keepUpdatedAt, dropUpdatedAt))
    )

    private fun mergeLinksBody(
        keep: Links,
        drop: Links,
        typeProperties: Map<String, Any?> = emptyMap(),
        keepUpdatedAt: Instant = keep.updatedAt ?: keep.createdAt!!,
        dropUpdatedAt: Instant = drop.updatedAt ?: drop.createdAt!!
    ): String = objectMapper.writeValueAsString(
        mapOf(
            "keepId" to keep.id,
            "dropId" to drop.id,
            "typeProperties" to typeProperties,
            "keepUpdatedAt" to keepUpdatedAt.toString(),
            "dropUpdatedAt" to dropUpdatedAt.toString()
        )
    )

    private fun mergeNodesBody(
        keep: Nodes,
        drop: Nodes,
        typeProperties: Map<String, Any?> = emptyMap(),
        transferLinkIds: List<UUID> = emptyList(),
        keepUpdatedAt: Instant = keep.updatedAt ?: keep.createdAt!!,
        dropUpdatedAt: Instant = drop.updatedAt ?: drop.createdAt!!
    ): String = objectMapper.writeValueAsString(
        mapOf(
            "keepId" to keep.id,
            "dropId" to drop.id,
            "typeProperties" to typeProperties,
            "transferLinkIds" to transferLinkIds,
            "keepUpdatedAt" to keepUpdatedAt.toString(),
            "dropUpdatedAt" to dropUpdatedAt.toString()
        )
    )

    private fun saveUser(role: Role): Users = usersRepository.save(
        Users(
            email = "validation-${UUID.randomUUID()}@test.com",
            role = role,
            createdAt = Instant.now()
        )
    )

    private fun saveModel(modelOwner: Users): Models = modelsRepository.save(
        Models(
            name = "validation-model-${UUID.randomUUID()}",
            version = "1.0.0",
            owner = modelOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNodeType(typeOwner: Users, name: String): NodeTypes = nodeTypesRepository.save(
        NodeTypes(
            name = name,
            owner = typeOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveLinkType(typeOwner: Users, name: String): LinkTypes = linkTypesRepository.save(
        LinkTypes(
            name = name,
            owner = typeOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveNode(
        targetModel: Models,
        name: String,
        type: NodeTypes,
        parent: Nodes? = null,
        attrs: String? = null,
        updatedAt: Instant = NODE_STAMP
    ): Nodes = nodesRepository.save(
        Nodes(
            stableId = UUID.randomUUID(),
            name = name,
            model = targetModel,
            owner = targetModel.owner,
            nodeType = type,
            parentNode = parent,
            createdAt = Instant.now(),
            updatedAt = updatedAt,
            attrs = attrs
        )
    )

    private fun saveLink(
        targetModel: Models,
        source: Nodes,
        target: Nodes,
        type: LinkTypes,
        attrs: String? = null,
        updatedAt: Instant = NODE_STAMP
    ): Links = linksRepository.save(
        Links(
            stableId = UUID.randomUUID(),
            model = targetModel,
            owner = targetModel.owner,
            linkType = type,
            source = source,
            target = target,
            createdAt = Instant.now(),
            updatedAt = updatedAt,
            attrs = attrs
        )
    )

    private fun saveNotation(notationOwner: Users): Notations = notationsRepository.save(
        Notations(
            name = "validation-notation-${UUID.randomUUID()}",
            version = "1.0.0",
            owner = notationOwner,
            createdAt = Instant.now()
        )
    )

    private fun saveDiagram(
        targetModel: Models,
        name: String,
        attrs: String,
        node: Nodes? = null
    ): Diagrams = diagramsRepository.save(
        Diagrams(
            name = name,
            version = "1.0.${diagramVersionCounter++}",
            owner = targetModel.owner,
            model = targetModel,
            notation = notation,
            node = node,
            attrs = attrs,
            createdAt = Instant.now()
        )
    )

    private fun attrsForLink(linkId: UUID): String =
        """{"instances":{"edges":[{"modelLinkId":"$linkId"}]}}"""

    private companion object {
        val NODE_STAMP: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
