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
import ru.kavader.arepos.dto.model.DiagramCopyCommitRequest
import ru.kavader.arepos.dto.model.DiagramCopyEntityKind
import ru.kavader.arepos.dto.model.DiagramCopyPreviewRequest
import ru.kavader.arepos.dto.model.DiagramCopyResolution
import ru.kavader.arepos.dto.model.DiagramCopyResolutionAction
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.repository.UsersRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class DiagramCopyControllerTest : ControllerIntegrationTest() {

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
    lateinit var linkTypesRepository: LinkTypesRepository

    @Autowired
    lateinit var nodesRepository: NodesRepository

    @Autowired
    lateinit var linksRepository: LinksRepository

    @Autowired
    lateinit var notationsRepository: NotationsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Test
    fun `preview reports stable id matches and can commit`() {
        val fixture = fixture()
        val sourceNode = fixture.node(fixture.sourceModel, "Source", stableId = UUID.randomUUID())
        val targetNode = fixture.node(
            fixture.targetModel,
            "Target",
            stableId = sourceNode.stableId,
            parentNode = fixture.targetRoot
        )
        val sourceDiagram = fixture.diagramWithNodes(sourceNode)

        mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/preview")
                .withAuth(fixture.owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fixture.previewRequest(sourceDiagram)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canCommit").value(true))
            .andExpect(jsonPath("$.nodes[0].autoMatchTargetId").value(targetNode.id.toString()))
            .andExpect(jsonPath("$.nodes[0].autoMatchReason").value("STABLE_ID"))
            .andExpect(jsonPath("$.nodes[0].effectiveAction").value("MATCH"))
            .andExpect(jsonPath("$.nodes[0].effectiveTargetId").value(targetNode.id.toString()))
    }

    @Test
    fun `commit creates nodes under mirrored source folder path`() {
        val fixture = fixture()
        val directoryType = nodeTypesRepository.save(
            NodeTypes(name = "Directory", owner = fixture.owner, createdAt = Instant.now())
        )
        val componentType = fixture.nodeType
        val sourceRoot = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "__model_tree_root__",
                model = fixture.sourceModel,
                owner = fixture.owner,
                nodeType = directoryType,
                attrs = """{"system":{"hiddenTreeRoot":true}}""",
                createdAt = Instant.now()
            )
        )
        val sourceFolder = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Business area",
                model = fixture.sourceModel,
                owner = fixture.owner,
                nodeType = directoryType,
                parentNode = sourceRoot,
                createdAt = Instant.now()
            )
        )
        val sourceNode = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Service",
                model = fixture.sourceModel,
                owner = fixture.owner,
                nodeType = componentType,
                parentNode = sourceFolder,
                createdAt = Instant.now()
            )
        )
        val sourceDiagram = fixture.diagramWithNodes(sourceNode)

        val response = mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/commit")
                .withAuth(fixture.owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        fixture.commitRequest(
                            sourceDiagram,
                            resolutions = listOf(fixture.create(sourceNode.id!!, DiagramCopyEntityKind.NODE))
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.createdNodeIds.length()").value(2))
            .andReturn()
            .response
            .contentAsString

        val createdNodeIds = objectMapper.readTree(response)["createdNodeIds"]
            .map { UUID.fromString(it.asText()) }
            .toSet()
        val createdNodes = nodesRepository.findAllById(createdNodeIds)
        val folder = createdNodes.single { it.nodeType.id == directoryType.id }
        val service = createdNodes.single { it.name == "Service" }

        assertEquals("Business area", folder.name)
        assertEquals(fixture.targetRoot.id, folder.parentNode?.id)
        assertEquals(folder.id, service.parentNode?.id)
    }

    @Test
    fun `preview reports name and type matches`() {
        val fixture = fixture()
        val sourceNode = fixture.node(fixture.sourceModel, "Same name", stableId = UUID.randomUUID())
        val targetNode = fixture.node(fixture.targetModel, "Same name", stableId = UUID.randomUUID())
        val sourceDiagram = fixture.diagramWithNodes(sourceNode)

        mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/preview")
                .withAuth(fixture.owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fixture.previewRequest(sourceDiagram)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nodes[0].autoMatchTargetId").value(targetNode.id.toString()))
            .andExpect(jsonPath("$.nodes[0].autoMatchReason").value("NAME_AND_TYPE"))
    }

    @Test
    fun `commit match only remaps instance ids without modifying source attrs`() {
        val fixture = fixture()
        val sourceNode = fixture.node(fixture.sourceModel, "Source", stableId = UUID.randomUUID())
        val targetNode = fixture.node(
            fixture.targetModel,
            "Target",
            stableId = sourceNode.stableId,
            parentNode = fixture.targetRoot
        )
        val sourceDiagram = fixture.diagramWithNodes(sourceNode)
        val sourceAttrs = requireNotNull(sourceDiagram.attrs)

        mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/commit")
                .withAuth(fixture.owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        fixture.commitRequest(
                            sourceDiagram,
                            resolutions = listOf(fixture.match(sourceNode.id!!, targetNode.id!!, DiagramCopyEntityKind.NODE))
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.createdNodeIds.length()").value(0))
            .andExpect(jsonPath("$.createdLinkIds.length()").value(0))

        val copied = diagramsRepository.findAll().single { it.id != sourceDiagram.id }
        assertEquals(targetNode.id.toString(), objectMapper.readTree(copied.attrs)["instances"]["nodes"][0]["modelNodeId"].asText())
        assertEquals(
            objectMapper.readTree(sourceAttrs),
            objectMapper.readTree(diagramsRepository.findById(sourceDiagram.id!!).orElseThrow().attrs)
        )
    }

    @Test
    fun `commit creates nodes and link preserving free stable ids`() {
        val fixture = fixture()
        val sourceA = fixture.node(fixture.sourceModel, "A", stableId = UUID.randomUUID())
        val sourceB = fixture.node(fixture.sourceModel, "B", stableId = UUID.randomUUID())
        val sourceLink = fixture.link(fixture.sourceModel, sourceA, sourceB, stableId = UUID.randomUUID())
        val sourceDiagram = fixture.diagramWithNodesAndLink(sourceA, sourceB, sourceLink)

        val response = mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/commit")
                .withAuth(fixture.owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        fixture.commitRequest(
                            sourceDiagram,
                            resolutions = listOf(
                                fixture.create(sourceA.id!!, DiagramCopyEntityKind.NODE),
                                fixture.create(sourceB.id!!, DiagramCopyEntityKind.NODE),
                                fixture.create(sourceLink.id!!, DiagramCopyEntityKind.LINK)
                            )
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.createdNodeIds.length()").value(2))
            .andExpect(jsonPath("$.createdLinkIds.length()").value(1))
            .andReturn()
            .response
            .contentAsString

        val createdNodeIds = objectMapper.readTree(response)["createdNodeIds"].map { UUID.fromString(it.asText()) }.toSet()
        val createdLinkId = UUID.fromString(objectMapper.readTree(response)["createdLinkIds"][0].asText())
        val createdNodes = nodesRepository.findAllById(createdNodeIds)
        val createdLink = linksRepository.findById(createdLinkId).orElseThrow()

        assertEquals(setOf(sourceA.stableId, sourceB.stableId), createdNodes.map { it.stableId }.toSet())
        assertEquals(sourceLink.stableId, createdLink.stableId)
        assertEquals(createdNodeIds, setOf(createdLink.source.id, createdLink.target.id))
    }

    @Test
    fun `commit rejects skipped edge endpoints`() {
        val fixture = fixture()
        val sourceA = fixture.node(fixture.sourceModel, "A")
        val sourceB = fixture.node(fixture.sourceModel, "B")
        val sourceLink = fixture.link(fixture.sourceModel, sourceA, sourceB)
        val sourceDiagram = fixture.diagramWithNodesAndLink(sourceA, sourceB, sourceLink)

        mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/commit")
                .withAuth(fixture.owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        fixture.commitRequest(
                            sourceDiagram,
                            resolutions = listOf(
                                fixture.skip(sourceA.id!!, DiagramCopyEntityKind.NODE),
                                fixture.skip(sourceB.id!!, DiagramCopyEntityKind.NODE),
                                fixture.skip(sourceLink.id!!, DiagramCopyEntityKind.LINK)
                            )
                        )
                    )
                )
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `preview denies user without target model edit access`() {
        val fixture = fixture()
        val sourceNode = fixture.node(fixture.sourceModel, "Source")
        val sourceDiagram = fixture.diagramWithNodes(sourceNode)
        val other = usersRepository.save(Users(email = "other-${UUID.randomUUID()}@test.com", role = Role.USER, createdAt = Instant.now()))

        mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/preview")
                .withAuth(other.id!!, Role.USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fixture.previewRequest(sourceDiagram)))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `commit returns conflict for duplicate target diagram name and version`() {
        val fixture = fixture()
        val sourceNode = fixture.node(fixture.sourceModel, "Source", stableId = UUID.randomUUID())
        val targetNode = fixture.node(
            fixture.targetModel,
            "Target",
            stableId = sourceNode.stableId,
            parentNode = fixture.targetRoot
        )
        val sourceDiagram = fixture.diagramWithNodes(sourceNode)
        fixture.diagram(fixture.targetModel, "Copied", "1.0.0", """{"instances":{"nodes":[],"edges":[]}}""")

        mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/commit")
                .withAuth(fixture.owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        fixture.commitRequest(
                            sourceDiagram,
                            resolutions = listOf(fixture.match(sourceNode.id!!, targetNode.id!!, DiagramCopyEntityKind.NODE))
                        )
                    )
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Diagram 'Copied' version '1.0.0' already exists in the target model"))
    }

    @Test
    fun `commit reports conflict when a deleted diagram occupies the same name and version`() {
        val fixture = fixture()
        val sourceNode = fixture.node(fixture.sourceModel, "Source", stableId = UUID.randomUUID())
        val targetNode = fixture.node(
            fixture.targetModel,
            "Target",
            stableId = sourceNode.stableId,
            parentNode = fixture.targetRoot
        )
        val sourceDiagram = fixture.diagramWithNodes(sourceNode)
        val deleted = fixture.diagram(fixture.targetModel, "Copied", "1.0.0", """{"instances":{"nodes":[],"edges":[]}}""")
        deleted.deleted = true
        diagramsRepository.saveAndFlush(deleted)

        mockMvc.perform(
            post("/api/v1/models/${fixture.targetModel.id}/diagram-copies/commit")
                .withAuth(fixture.owner.id!!)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        fixture.commitRequest(
                            sourceDiagram,
                            resolutions = listOf(fixture.match(sourceNode.id!!, targetNode.id!!, DiagramCopyEntityKind.NODE))
                        )
                    )
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Diagram 'Copied' version '1.0.0' already exists in the target model"))
    }

    private fun fixture(): CopyFixture {
        val now = Instant.now()
        val owner = usersRepository.save(Users(email = "copy-${UUID.randomUUID()}@test.com", role = Role.ADMIN, createdAt = now))
        val nodeType = nodeTypesRepository.save(NodeTypes(name = "node-${UUID.randomUUID()}", owner = owner, createdAt = now))
        val linkType = linkTypesRepository.save(LinkTypes(name = "link-${UUID.randomUUID()}", owner = owner, createdAt = now))
        val sourceModel = modelsRepository.save(Models(name = "source-${UUID.randomUUID()}", version = "1.0.0", owner = owner, createdAt = now))
        val targetModel = modelsRepository.save(Models(name = "target-${UUID.randomUUID()}", version = "1.0.0", owner = owner, createdAt = now))
        val notation = notationsRepository.save(Notations(name = "notation-${UUID.randomUUID()}", version = "1.0.0", owner = owner, createdAt = now))
        val targetRoot = nodesRepository.save(
            Nodes(
                stableId = UUID.randomUUID(),
                name = "Target root",
                model = targetModel,
                owner = owner,
                nodeType = nodeType,
                createdAt = now
            )
        )
        return CopyFixture(owner, sourceModel, targetModel, notation, nodeType, linkType, targetRoot)
    }

    private inner class CopyFixture(
        val owner: Users,
        val sourceModel: Models,
        val targetModel: Models,
        val notation: Notations,
        val nodeType: NodeTypes,
        val linkType: LinkTypes,
        val targetRoot: Nodes
    ) {
        fun node(
            model: Models,
            name: String,
            stableId: UUID = UUID.randomUUID(),
            parentNode: Nodes? = null
        ): Nodes = nodesRepository.save(
            Nodes(
                stableId = stableId,
                name = name,
                model = model,
                owner = owner,
                nodeType = nodeType,
                parentNode = parentNode,
                createdAt = Instant.now()
            )
        )

        fun link(model: Models, source: Nodes, target: Nodes, stableId: UUID = UUID.randomUUID()): Links = linksRepository.save(
            Links(
                stableId = stableId,
                source = source,
                target = target,
                model = model,
                owner = owner,
                linkType = linkType,
                createdAt = Instant.now()
            )
        )

        fun diagramWithNodes(vararg nodes: Nodes): Diagrams = diagram(
            sourceModel,
            "Source diagram",
            "1.0.0",
            """{"instances":{"nodes":[${nodes.joinToString { """{"id":"node-${it.id}","modelNodeId":"${it.id}"}""" }}],"edges":[]}}"""
        )

        fun diagramWithNodesAndLink(source: Nodes, target: Nodes, link: Links): Diagrams = diagram(
            sourceModel,
            "Source diagram",
            "1.0.0",
            """{"instances":{"nodes":[{"id":"source","modelNodeId":"${source.id}"},{"id":"target","modelNodeId":"${target.id}"}],"edges":[{"id":"edge","modelLinkId":"${link.id}","sourceModelNodeId":"${source.id}","targetModelNodeId":"${target.id}"}]}}"""
        )

        fun diagram(model: Models, name: String, version: String, attrs: String): Diagrams = diagramsRepository.save(
            Diagrams(
                name = name,
                version = version,
                model = model,
                notation = notation,
                owner = owner,
                attrs = attrs,
                createdAt = Instant.now()
            )
        )

        fun previewRequest(sourceDiagram: Diagrams): DiagramCopyPreviewRequest =
            DiagramCopyPreviewRequest(sourceDiagram.id!!, notation.id!!)

        fun commitRequest(
            sourceDiagram: Diagrams,
            resolutions: List<DiagramCopyResolution>
        ): DiagramCopyCommitRequest = DiagramCopyCommitRequest(
            sourceDiagramId = sourceDiagram.id!!,
            targetNotationId = notation.id!!,
            name = "Copied",
            version = "1.0.0",
            resolutions = resolutions
        )

        fun match(sourceId: UUID, targetId: UUID, kind: DiagramCopyEntityKind): DiagramCopyResolution =
            DiagramCopyResolution(sourceId, DiagramCopyResolutionAction.MATCH, targetId, kind)

        fun create(sourceId: UUID, kind: DiagramCopyEntityKind): DiagramCopyResolution =
            DiagramCopyResolution(sourceId, DiagramCopyResolutionAction.CREATE, kind = kind)

        fun skip(sourceId: UUID, kind: DiagramCopyEntityKind): DiagramCopyResolution =
            DiagramCopyResolution(sourceId, DiagramCopyResolutionAction.SKIP, kind = kind)
    }
}
