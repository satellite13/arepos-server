package ru.kavader.arepos.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.DeleteUnusedRequest
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.DuplicateLinkMemberProjection
import ru.kavader.arepos.repository.DuplicateNodeMemberProjection
import ru.kavader.arepos.repository.LinkEndpointProjection
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodeIdProjection
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.RefUsageProjection
import ru.kavader.arepos.repository.UnusedLinkProjection
import ru.kavader.arepos.repository.UnusedNodeProjection
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.service.ModelSyncBroadcaster
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelValidationReportServiceTest {

    private val modelsRepository = mock(ModelsRepository::class.java)
    private val accessService = mock(ResourceAccessService::class.java)
    private val nodesRepository = mock(NodesRepository::class.java)
    private val linksRepository = mock(LinksRepository::class.java)
    private val diagramsRepository = mock(DiagramsRepository::class.java)
    private val objectMapper = ObjectMapper()

    private val modelSyncBroadcaster = mock(ModelSyncBroadcaster::class.java)

    private val service = ModelValidationReportService(
        modelsRepository = modelsRepository,
        accessService = accessService,
        nodesRepository = nodesRepository,
        linksRepository = linksRepository,
        diagramsRepository = diagramsRepository,
        modelSyncBroadcaster = modelSyncBroadcaster,
        objectMapper = objectMapper,
        maxGroups = 200,
        maxDiagrams = 500,
        maxIssuesPerDiagram = 50,
        maxUnused = 500,
        maxMembersPerGroup = 50,
        excludedViewFolders = "_СоМ (Соглашение о Моделировании)"
    )

    private val owner = Users(id = UUID.randomUUID(), email = "report@test.com")
    private val modelId = UUID.randomUUID()
    private val model = Models(
        id = modelId,
        name = "m",
        version = "1.0.0",
        owner = owner,
        createdAt = Instant.now()
    )
    private val notation = Notations(
        id = UUID.randomUUID(),
        owner = owner,
        name = "Archimate 3.1",
        version = "1.0.0",
        createdAt = Instant.now()
    )
    private val now = Instant.parse("2024-01-01T00:00:00Z")

    @Test
    fun `clean diagram produces no diagram issues`() {
        val nodeA = UUID.randomUUID()
        val nodeB = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        stubModel(listOf(nodeA, nodeB), mapOf(linkId to (nodeA to nodeB)))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[
                            {"id":"i1","modelNodeId":"$nodeA","x":0,"y":0},
                            {"id":"i2","modelNodeId":"$nodeB","x":10,"y":10}
                        ],
                        "edges":[
                            {"id":"e1","modelLinkId":"$linkId","sourceInstanceId":"i1","targetInstanceId":"i2"}
                        ]
                    }}"""
                )
            )
        )

        val report = service.report(modelId)

        assertEquals(0, report.diagramIssuesTotal)
        assertTrue(report.diagramIssues.isEmpty())
    }

    @Test
    fun `dangling node instance reference is reported`() {
        val missingNode = UUID.randomUUID()
        stubModel(emptyList())
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{"nodes":[{"id":"i1","modelNodeId":"$missingNode","x":0,"y":0}],"edges":[]}}"""
                )
            )
        )

        val issues = service.report(modelId).diagramIssues

        assertEquals(1, issues.size)
        val codes = issues.single().issues.map { it.code }
        assertEquals(listOf("diagramNodeDanglingRef"), codes)
        assertEquals("i1", issues.single().issues.single().instanceId)
    }

    @Test
    fun `edge endpoints not matching model link ends are reported`() {
        val nodeA = UUID.randomUUID()
        val nodeB = UUID.randomUUID()
        val nodeC = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        stubModel(listOf(nodeA, nodeB, nodeC), mapOf(linkId to (nodeA to nodeB)))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[
                            {"id":"i1","modelNodeId":"$nodeA","x":0,"y":0},
                            {"id":"i2","modelNodeId":"$nodeC","x":10,"y":10}
                        ],
                        "edges":[
                            {"id":"e1","modelLinkId":"$linkId","sourceInstanceId":"i1","targetInstanceId":"i2"}
                        ]
                    }}"""
                )
            )
        )

        val issues = service.report(modelId).diagramIssues

        val codes = issues.single().issues.map { it.code }
        assertEquals(listOf("diagramLinkEndpointMismatch"), codes)
        assertEquals("e1", issues.single().issues.single().instanceId)
    }

    @Test
    fun `swapped edge endpoints are reported as mismatch`() {
        val nodeA = UUID.randomUUID()
        val nodeB = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        stubModel(listOf(nodeA, nodeB), mapOf(linkId to (nodeA to nodeB)))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[
                            {"id":"i1","modelNodeId":"$nodeA","x":0,"y":0},
                            {"id":"i2","modelNodeId":"$nodeB","x":10,"y":10}
                        ],
                        "edges":[
                            {"id":"e1","modelLinkId":"$linkId","sourceInstanceId":"i2","targetInstanceId":"i1"}
                        ]
                    }}"""
                )
            )
        )

        val issues = service.report(modelId).diagramIssues

        assertEquals(listOf("diagramLinkEndpointMismatch"), issues.single().issues.map { it.code })
    }

    @Test
    fun `edge with missing endpoint instance is reported`() {
        val nodeA = UUID.randomUUID()
        val nodeB = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        stubModel(listOf(nodeA, nodeB), mapOf(linkId to (nodeA to nodeB)))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[
                            {"id":"i1","modelNodeId":"$nodeA","x":0,"y":0},
                            {"id":"i2","modelNodeId":"$nodeB","x":10,"y":10}
                        ],
                        "edges":[
                            {"id":"e1","modelLinkId":"$linkId","sourceInstanceId":"ghost","targetInstanceId":"i2"}
                        ]
                    }}"""
                )
            )
        )

        val issues = service.report(modelId).diagramIssues

        assertEquals(listOf("diagramEdgeMissingEndpointInstance"), issues.single().issues.map { it.code })
    }

    @Test
    fun `dangling edge model link is reported`() {
        val nodeA = UUID.randomUUID()
        val missingLink = UUID.randomUUID()
        stubModel(listOf(nodeA))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[{"id":"i1","modelNodeId":"$nodeA","x":0,"y":0}],
                        "edges":[
                            {"id":"e1","modelLinkId":"$missingLink","sourceInstanceId":"i1","targetInstanceId":"i1"}
                        ]
                    }}"""
                )
            )
        )

        val issues = service.report(modelId).diagramIssues

        assertEquals(listOf("diagramLinkDanglingRef"), issues.single().issues.map { it.code })
    }

    @Test
    fun `duplicate instance ids are reported per kind`() {
        val nodeA = UUID.randomUUID()
        stubModel(listOf(nodeA))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[
                            {"id":"i1","modelNodeId":"$nodeA","x":0,"y":0},
                            {"id":"i1","modelNodeId":"$nodeA","x":5,"y":5}
                        ],
                        "edges":[
                            {"id":"e1","modelLinkId":"","sourceInstanceId":"i1","targetInstanceId":"i1"},
                            {"id":"e1","modelLinkId":"","sourceInstanceId":"i1","targetInstanceId":"i1"}
                        ]
                    }}"""
                )
            )
        )

        val issues = service.report(modelId).diagramIssues

        val messages = issues.single().issues.map { it.message }
        assertEquals(2, issues.single().issues.size)
        assertTrue(messages.any { it.startsWith("Node instance id") })
        assertTrue(messages.any { it.startsWith("Edge instance id") })
    }

    @Test
    fun `note edges and diagram only edges are skipped`() {
        val nodeA = UUID.randomUUID()
        stubModel(listOf(nodeA))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[{"id":"i1","modelNodeId":"$nodeA","x":0,"y":0}],
                        "edges":[
                            {"id":"e1","modelLinkId":"__diagram-note-edge__:abc","sourceInstanceId":"i1","targetInstanceId":"i1"},
                            {"id":"e2","modelLinkId":"whatever","attrs":{"isDiagramOnly":true},"sourceInstanceId":"i1","targetInstanceId":"i1"}
                        ]
                    }}"""
                )
            )
        )

        val report = service.report(modelId)

        assertEquals(0, report.diagramIssuesTotal)
    }

    @Test
    fun `diagram only node instances and their note edges are not reported`() {
        val nodeA = UUID.randomUUID()
        stubModel(listOf(nodeA))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[
                            {"id":"i1","modelNodeId":"$nodeA","x":0,"y":0},
                            {"id":"n1","modelNodeId":"__diagram-note__:abc","attrs":{"isNote":true,"noteText":"x"}},
                            {"id":"c1","modelNodeId":"__diagram-container__:abc","attrs":{"isContainer":true}},
                            {"id":"a1","modelNodeId":"__diagram-edge-anchor__:abc","attrs":{"isEdgeAnchor":true}}
                        ],
                        "edges":[
                            {"id":"ne1","modelLinkId":"__diagram-note-edge__:abc","sourceInstanceId":"a1","targetInstanceId":"n1"},
                            {"id":"ne2","modelLinkId":"__diagram-note-edge__:def","sourceInstanceId":"c1","targetInstanceId":"n1"}
                        ]
                    }}"""
                )
            )
        )

        val report = service.report(modelId)

        assertEquals(0, report.diagramIssuesTotal)
        assertTrue(report.diagramIssues.isEmpty())
    }

    @Test
    fun `genuinely invalid non-uuid model node id is still reported`() {
        stubModel(emptyList())
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{"nodes":[{"id":"i1","modelNodeId":"junk","x":0,"y":0}],"edges":[]}}"""
                )
            )
        )

        val issues = service.report(modelId).diagramIssues

        assertEquals(listOf("diagramNodeInvalidModelRef"), issues.single().issues.map { it.code })
    }

    @Test
    fun `untyped diagram only edges are skipped`() {
        val nodeA = UUID.randomUUID()
        stubModel(listOf(nodeA))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[
                            {"id":"i1","modelNodeId":"$nodeA","x":0,"y":0},
                            {"id":"n1","modelNodeId":"__diagram-note__:abc","attrs":{"isNote":true}}
                        ],
                        "edges":[
                            {"id":"ue1","modelLinkId":"__diagram-untyped-edge__:abc","sourceInstanceId":"i1","targetInstanceId":"n1"}
                        ]
                    }}"""
                )
            )
        )

        val report = service.report(modelId)

        assertEquals(0, report.diagramIssuesTotal)
    }

    @Test
    fun `edge endpoint pointing to diagram only instance is not reported as missing`() {
        val nodeA = UUID.randomUUID()
        stubModel(listOf(nodeA))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[
                            {"id":"i1","modelNodeId":"$nodeA","x":0,"y":0},
                            {"id":"n1","modelNodeId":"__diagram-note__:abc","attrs":{"isNote":true}}
                        ],
                        "edges":[
                            {"id":"e1","modelLinkId":"","sourceInstanceId":"i1","targetInstanceId":"n1"}
                        ]
                    }}"""
                )
            )
        )

        val report = service.report(modelId)

        assertEquals(0, report.diagramIssuesTotal)
    }

    @Test
    fun `only latest diagram version is checked`() {
        val nodeA = UUID.randomUUID()
        val nodeB = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        stubModel(listOf(nodeA, nodeB), mapOf(linkId to (nodeA to nodeB)))
        // Latest version is broken; older clean version must be ignored.
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{"nodes":[{"id":"i1","modelNodeId":"$nodeA","x":0,"y":0}],"edges":[]}}""",
                    version = "1.0.0"
                ),
                diagram(
                    "d",
                    """{"instances":{
                        "nodes":[{"id":"i1","modelNodeId":"$nodeA","x":0,"y":0}],
                        "edges":[{"id":"e1","modelLinkId":"$linkId","sourceInstanceId":"i1","targetInstanceId":"ghost"}]
                    }}""",
                    version = "1.1.0"
                )
            )
        )

        val issues = service.report(modelId).diagramIssues

        assertEquals(listOf("diagramEdgeMissingEndpointInstance"), issues.single().issues.map { it.code })
    }

    @Test
    fun `duplicate groups are annotated with diagram usage and sorted by it`() {
        val nodeA = UUID.randomUUID()
        val nodeB = UUID.randomUUID()
        val nodeC = UUID.randomUUID()
        val groupTypeId = UUID.randomUUID()
        stubModel(listOf(nodeA, nodeB, nodeC))
        // Группы из SQL приходят в произвольном порядке: A (1 диаграмма), B (5 диаграмм).
        val memberA = duplicateNodeMemberProjection(nodeA, "A", groupTypeId, groupCount = 2)
        val memberB1 = duplicateNodeMemberProjection(nodeB, "B", groupTypeId, groupCount = 2)
        val memberB2 = duplicateNodeMemberProjection(nodeC, "B", groupTypeId, groupCount = 2)
        `when`(nodesRepository.findDuplicateNodeMembers(modelId, 200, 50))
            .thenReturn(listOf(memberA, memberB1, memberB2))
        val nodeUsages = listOf(usage(nodeA, 1), usage(nodeB, 5), usage(nodeC, 2))
        `when`(nodesRepository.findDiagramUsageByNodeIds(modelId, listOf(nodeA.toString(), nodeB.toString(), nodeC.toString())))
            .thenReturn(nodeUsages)

        val report = service.report(modelId)

        val groups = report.duplicateNodes
        assertEquals(2, groups.size)
        // Самая используемая группа (B, max=5) — первая; её члены отсортированы по использованию.
        assertEquals("B", groups[0].name)
        assertEquals(5, groups[0].nodes[0].diagramCount)
        assertEquals(nodeB, groups[0].nodes[0].id)
        assertEquals(2, groups[0].nodes[1].diagramCount)
        assertEquals(1, groups[1].nodes[0].diagramCount)
    }

    @Test
    fun `link members are annotated with diagram usage`() {
        val nodeA = UUID.randomUUID()
        val nodeB = UUID.randomUUID()
        val linkA = UUID.randomUUID()
        val linkB = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val linkTypeId = UUID.randomUUID()
        stubModel(listOf(nodeA, nodeB), mapOf(linkA to (nodeA to nodeB), linkB to (nodeA to nodeB)))
        val memberA = duplicateLinkMemberProjection(linkA, sourceId, targetId, linkTypeId)
        val memberB = duplicateLinkMemberProjection(linkB, sourceId, targetId, linkTypeId)
        `when`(linksRepository.findDuplicateLinkMembers(modelId, 200, 50)).thenReturn(listOf(memberA, memberB))
        val linkUsages = listOf(usage(linkA, 3), usage(linkB, 1))
        `when`(linksRepository.findDiagramUsageByLinkIds(modelId, listOf(linkA.toString(), linkB.toString())))
            .thenReturn(linkUsages)

        val report = service.report(modelId)

        val group = report.duplicateLinks.single()
        assertEquals(listOf(linkA to 3, linkB to 1), group.links.map { it.id to it.diagramCount })
    }

    @Test
    fun `issues per diagram are capped`() {
        val nodeA = UUID.randomUUID()
        val capped = ModelValidationReportService(
            modelsRepository = modelsRepository,
            accessService = accessService,
            nodesRepository = nodesRepository,
            linksRepository = linksRepository,
            diagramsRepository = diagramsRepository,
            modelSyncBroadcaster = modelSyncBroadcaster,
            objectMapper = objectMapper,
            maxGroups = 200,
            maxDiagrams = 500,
            maxIssuesPerDiagram = 2,
            maxUnused = 500,
        maxMembersPerGroup = 50,
            excludedViewFolders = "_СоМ (Соглашение о Моделировании)"
        )
        stubModel(listOf(nodeA))
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{"nodes":[
                        {"id":"i1","modelNodeId":"${UUID.randomUUID()}","x":0,"y":0},
                        {"id":"i2","modelNodeId":"${UUID.randomUUID()}","x":1,"y":1},
                        {"id":"i3","modelNodeId":"${UUID.randomUUID()}","x":2,"y":2}
                    ],"edges":[]}}"""
                )
            )
        )

        val report = capped.report(modelId)

        assertEquals(2, report.diagramIssuesTotal)
        assertEquals(2, report.diagramIssues.single().issues.size)
    }

    @Test
    fun `members placed on excluded folder diagrams are dropped from duplicate groups`() {
        val nodeA = UUID.randomUUID()
        val nodeB = UUID.randomUUID()
        val nodeC = UUID.randomUUID()
        val groupTypeId = UUID.randomUUID()
        stubModel(listOf(nodeA, nodeB, nodeC))
        val memberA = duplicateNodeMemberProjection(nodeA, "A", groupTypeId, groupCount = 3)
        val memberB = duplicateNodeMemberProjection(nodeB, "A", groupTypeId, groupCount = 3)
        val memberC = duplicateNodeMemberProjection(nodeC, "A", groupTypeId, groupCount = 3)
        `when`(nodesRepository.findDuplicateNodeMembers(modelId, 200, 50))
            .thenReturn(listOf(memberA, memberB, memberC))
        // nodeB размещён на диаграмме в папке-исключении → выпадает из группы, остальные остаются.
        `when`(
            nodesRepository.findNodeRefsInExcludedDiagrams(
                modelId, "_СоМ (Соглашение о Моделировании)", listOf(nodeA.toString(), nodeB.toString(), nodeC.toString())
            )
        ).thenReturn(listOf(nodeB.toString()))
        val nodeUsages = listOf(usage(nodeA, 1), usage(nodeC, 2))
        `when`(nodesRepository.findDiagramUsageByNodeIds(modelId, listOf(nodeA.toString(), nodeC.toString())))
            .thenReturn(nodeUsages)

        val report = service.report(modelId)

        assertEquals(listOf("A"), report.duplicateNodes.map { it.name })
        assertEquals(listOf(nodeC, nodeA), report.duplicateNodes.single().nodes.map { it.id })
        assertEquals(2, report.duplicateNodes.single().count)
    }

    @Test
    fun `diagrams under excluded folder are not scanned for integrity issues`() {
        val missingNode = UUID.randomUUID()
        stubModel(emptyList())
        val somDiagramId = UUID.randomUUID()
        val somDiagram = diagram(
            "СоМ-схема",
            """{"instances":{"nodes":[{"id":"i1","modelNodeId":"$missingNode","x":0,"y":0}],"edges":[]}}""",
            id = somDiagramId
        )
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(listOf(somDiagram))
        `when`(
            diagramsRepository.findDiagramIdsUnderFolder(modelId, "_СоМ (Соглашение о Моделировании)")
        ).thenReturn(listOf(somDiagramId))

        val report = service.report(modelId)

        assertEquals(0, report.diagramIssuesTotal)
        assertTrue(report.diagramIssues.isEmpty())
    }

    @Test
    fun `excluded view folders are parsed as comma separated list`() {
        val missingNode = UUID.randomUUID()
        val multi = ModelValidationReportService(
            modelsRepository = modelsRepository,
            accessService = accessService,
            nodesRepository = nodesRepository,
            linksRepository = linksRepository,
            diagramsRepository = diagramsRepository,
            modelSyncBroadcaster = modelSyncBroadcaster,
            objectMapper = objectMapper,
            maxGroups = 200,
            maxDiagrams = 500,
            maxIssuesPerDiagram = 50,
            maxUnused = 500,
        maxMembersPerGroup = 50,
            excludedViewFolders = " _СоМ (Соглашение о Моделировании) , Archive "
        )
        stubModel(emptyList())
        val somDiagramId = UUID.randomUUID()
        `when`(diagramsRepository.findAllActiveByModelId(modelId)).thenReturn(
            listOf(
                diagram(
                    "d",
                    """{"instances":{"nodes":[{"id":"i1","modelNodeId":"$missingNode","x":0,"y":0}],"edges":[]}}""",
                    id = somDiagramId
                )
            )
        )
        `when`(diagramsRepository.findDiagramIdsUnderFolder(modelId, "_СоМ (Соглашение о Моделировании)"))
            .thenReturn(listOf(somDiagramId))

        val report = multi.report(modelId)

        assertEquals(0, report.diagramIssuesTotal)
    }

    @Test
    fun `report includes unused nodes and links`() {
        stubModel(emptyList())
        val orphanId = UUID.randomUUID()
        val unusedNodeRow = object : UnusedNodeProjection {
            override fun getId() = orphanId
            override fun getName() = "orphan"
            override fun getParentId(): UUID? = null
            override fun getParentName(): String? = null
        }
        `when`(nodesRepository.countUnusedNodes(modelId)).thenReturn(1L)
        `when`(nodesRepository.findUnusedNodes(modelId, 500)).thenReturn(listOf(unusedNodeRow))
        val unusedLinkId = UUID.randomUUID()
        val unusedLinkRow = object : UnusedLinkProjection {
            override fun getId() = unusedLinkId
            override fun getSourceName() = "a"
            override fun getTargetName() = "b"
            override fun getLinkTypeName() = "Flow"
        }
        `when`(linksRepository.countUnusedLinks(modelId)).thenReturn(1L)
        `when`(linksRepository.findUnusedLinks(modelId, 500)).thenReturn(listOf(unusedLinkRow))

        val report = service.report(modelId)

        assertEquals(1, report.unusedNodesTotal)
        assertEquals(listOf("orphan"), report.unusedNodes.map { it.name })
        assertEquals(1, report.unusedLinksTotal)
        assertEquals(listOf(unusedLinkId), report.unusedLinks.map { it.id })
    }

    @Test
    fun `deleteUnused re-verifies ids, deletes links before nodes and broadcasts events`() {
        val nodeId = UUID.randomUUID()
        val staleNodeId = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        stubModel(emptyList())
        `when`(nodesRepository.findUnusedNodeIds(modelId, listOf(nodeId, staleNodeId)))
            .thenReturn(listOf(nodeId))
        `when`(linksRepository.findUnusedLinkIds(modelId, listOf(linkId)))
            .thenReturn(listOf(linkId))

        val response = service.deleteUnused(modelId, DeleteUnusedRequest(nodeIds = listOf(nodeId, staleNodeId), linkIds = listOf(linkId)))

        assertEquals(listOf(nodeId), response.deletedNodeIds)
        assertEquals(listOf(staleNodeId), response.skippedNodeIds)
        assertEquals(listOf(linkId), response.deletedLinkIds)
        verify(linksRepository).deleteAllByIdInBatch(listOf(linkId))
        verify(nodesRepository).deleteAllByIdInBatch(listOf(nodeId))
    }

    @Test
    fun `deleteUnused rejects batches above the limit`() {
        stubModel(emptyList())
        val tooMany = List(501) { UUID.randomUUID() }

        val ex = runCatching { service.deleteUnused(modelId, DeleteUnusedRequest(nodeIds = tooMany)) }

        assertTrue(ex.exceptionOrNull() is ResponseStatusException)
    }

    private fun duplicateNodeMemberProjection(
        id: UUID,
        name: String,
        nodeTypeId: UUID,
        groupCount: Int
    ): DuplicateNodeMemberProjection {
        val projection = mock(DuplicateNodeMemberProjection::class.java)
        `when`(projection.getNodeTypeId()).thenReturn(nodeTypeId)
        `when`(projection.getNodeTypeName()).thenReturn("ApplicationComponent")
        `when`(projection.getNameKey()).thenReturn(name.lowercase())
        `when`(projection.getGroupCount()).thenReturn(groupCount.toLong())
        `when`(projection.getTotalGroups()).thenReturn(1)
        `when`(projection.getId()).thenReturn(id)
        `when`(projection.getName()).thenReturn(name)
        `when`(projection.getParentId()).thenReturn(null)
        `when`(projection.getParentName()).thenReturn(null)
        return projection
    }

    private fun duplicateLinkMemberProjection(
        id: UUID,
        sourceId: UUID,
        targetId: UUID,
        linkTypeId: UUID
    ): DuplicateLinkMemberProjection {
        val projection = mock(DuplicateLinkMemberProjection::class.java)
        `when`(projection.getSourceId()).thenReturn(sourceId)
        `when`(projection.getSourceName()).thenReturn("src")
        `when`(projection.getTargetId()).thenReturn(targetId)
        `when`(projection.getTargetName()).thenReturn("tgt")
        `when`(projection.getLinkTypeId()).thenReturn(linkTypeId)
        `when`(projection.getLinkTypeName()).thenReturn("Serving")
        `when`(projection.getGroupCount()).thenReturn(2)
        `when`(projection.getTotalGroups()).thenReturn(1)
        `when`(projection.getId()).thenReturn(id)
        return projection
    }

    private fun usage(id: UUID, diagramCount: Long): RefUsageProjection {
        val projection = mock(RefUsageProjection::class.java)
        `when`(projection.getRefId()).thenReturn(id.toString())
        `when`(projection.getDiagramCount()).thenReturn(diagramCount)
        return projection
    }

    private fun stubModel(nodeIds: List<UUID>, links: Map<UUID, Pair<UUID, UUID>> = emptyMap()) {
        `when`(modelsRepository.findById(modelId)).thenReturn(Optional.of(model))
        `when`(nodesRepository.findDuplicateNodeMembers(modelId, 200, 50)).thenReturn(emptyList())
        `when`(linksRepository.findDuplicateLinkMembers(modelId, 200, 50)).thenReturn(emptyList())
        val nodeIdProjections = nodeIds.map { id ->
            val projection = mock(NodeIdProjection::class.java)
            `when`(projection.getId()).thenReturn(id)
            projection
        }
        `when`(nodesRepository.findIdsByModelId(modelId)).thenReturn(nodeIdProjections)
        val linkProjections = links.map { (id, ends) ->
            val projection = mock(LinkEndpointProjection::class.java)
            `when`(projection.getId()).thenReturn(id)
            `when`(projection.getSourceId()).thenReturn(ends.first)
            `when`(projection.getTargetId()).thenReturn(ends.second)
            projection
        }
        `when`(linksRepository.findEndpointsByModelId(modelId)).thenReturn(linkProjections)
    }

    private fun diagram(name: String, attrs: String, version: String = "1.0.0", id: UUID = UUID.randomUUID()): Diagrams =
        Diagrams(
            id = id,
            name = name,
            attrs = attrs,
            version = version,
            owner = owner,
            model = model,
            notation = notation,
            createdAt = now,
            updatedAt = now
        )
}
