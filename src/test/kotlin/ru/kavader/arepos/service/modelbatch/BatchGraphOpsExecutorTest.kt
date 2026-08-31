package ru.kavader.arepos.service.modelbatch

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.BatchDeleteEntry
import ru.kavader.arepos.dto.model.BatchDiagramOps
import ru.kavader.arepos.dto.model.BatchDiagramUpdate
import ru.kavader.arepos.dto.model.BatchLinkCreate
import ru.kavader.arepos.dto.model.BatchLinkOps
import ru.kavader.arepos.dto.model.BatchLinkUpdate
import ru.kavader.arepos.dto.model.BatchNodeCreate
import ru.kavader.arepos.dto.model.BatchNodeOps
import ru.kavader.arepos.dto.model.BatchNodeUpdate
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.model.Diagrams
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Links
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinkTypesRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodeTypesRepository
import ru.kavader.arepos.repository.NodesRepository
import ru.kavader.arepos.repository.NotationsRepository
import ru.kavader.arepos.security.ResourceAccessService
import ru.kavader.arepos.security.TypeUsageAuthorization
import ru.kavader.arepos.service.DiagramCanvasInstancesCleanupService
import ru.kavader.arepos.service.DiagramLifecycleService
import ru.kavader.arepos.service.DiagramOnlyOrphanCleanupService
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BatchGraphOpsExecutorTest {

    private val nodesRepository = mock(NodesRepository::class.java)
    private val linksRepository = mock(LinksRepository::class.java)
    private val diagramsRepository = mock(DiagramsRepository::class.java)
    private val executor = BatchGraphOpsExecutor(
        nodesRepository = nodesRepository,
        linksRepository = linksRepository,
        diagramsRepository = diagramsRepository,
        nodeTypesRepository = mock(NodeTypesRepository::class.java),
        linkTypesRepository = mock(LinkTypesRepository::class.java),
        notationsRepository = mock(NotationsRepository::class.java),
        accessService = mock(ResourceAccessService::class.java),
        diagramCanvasInstancesCleanupService = mock(DiagramCanvasInstancesCleanupService::class.java),
        diagramOnlyOrphanCleanupService = mock(DiagramOnlyOrphanCleanupService::class.java),
        typeUsageAuthorization = mock(TypeUsageAuthorization::class.java),
        diagramAttrsRemapper = mock(DiagramAttrsRemapper::class.java),
        diagramLifecycleService = mock(DiagramLifecycleService::class.java)
    )
    private val owner = Users(id = UUID.randomUUID(), email = "batch-owner@test.com")
    private val model = Models(
        id = UUID.randomUUID(),
        name = "batch-model",
        version = "1.0.0",
        owner = owner,
        createdAt = Instant.now()
    )
    private val otherModel = Models(
        id = UUID.randomUUID(),
        name = "other-model",
        version = "1.0.0",
        owner = owner,
        createdAt = Instant.now()
    )
    private val nodeType = NodeTypes(
        id = UUID.randomUUID(),
        name = "Component",
        owner = owner,
        createdAt = Instant.now()
    )
    private val linkType = LinkTypes(
        id = UUID.randomUUID(),
        name = "Serving",
        owner = owner,
        createdAt = Instant.now()
    )
    private val notation = Notations(
        id = UUID.randomUUID(),
        name = "Archimate",
        version = "1.0.0",
        owner = owner,
        createdAt = Instant.now()
    )

    @Test
    fun `invalid external parent UUID remains pending and uses unresolvable fallback`() {
        val request = BatchSaveRequest(
            nodes = BatchNodeOps(
                create = listOf(
                    BatchNodeCreate(
                        tempId = "child",
                        name = "Child",
                        nodeTypeId = UUID.randomUUID(),
                        parentNodeId = "not-a-uuid"
                    )
                )
            )
        )

        val exception = assertFailsWith<ResponseStatusException> {
            executor.execute(request, model, owner, Instant.now())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals(
            "Circular or unresolvable parent references in node creates: [child]",
            exception.reason
        )
        verifyNoInteractions(nodesRepository)
    }

    @Test
    fun `invalid link endpoint UUID returns bad request validation error`() {
        val request = BatchSaveRequest(
            links = BatchLinkOps(
                create = listOf(
                    BatchLinkCreate(
                        tempId = "link",
                        sourceId = "not-a-uuid",
                        targetId = UUID.randomUUID().toString(),
                        linkTypeId = UUID.randomUUID()
                    )
                )
            )
        )

        val exception = assertFailsWith<ResponseStatusException> {
            executor.execute(request, model, owner, Instant.now())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Cannot resolve source node reference: not-a-uuid", exception.reason)
        verifyNoInteractions(nodesRepository, linksRepository)
    }

    @Test
    fun `update node from another model is rejected`() {
        val foreign = nodeOn(otherModel)
        `when`(nodesRepository.findById(foreign.id!!)).thenReturn(Optional.of(foreign))

        val exception = assertFailsWith<ResponseStatusException> {
            executor.execute(
                BatchSaveRequest(
                    nodes = BatchNodeOps(
                        update = listOf(
                            BatchNodeUpdate(
                                id = foreign.id!!,
                                name = "renamed",
                                nodeTypeId = nodeType.id!!
                            )
                        )
                    )
                ),
                model,
                owner,
                Instant.now()
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Node ${foreign.id} does not belong to model ${model.id}", exception.reason)
    }

    @Test
    fun `create node with parent from another model is rejected`() {
        val foreignParent = nodeOn(otherModel)
        `when`(nodesRepository.findById(foreignParent.id!!)).thenReturn(Optional.of(foreignParent))

        val exception = assertFailsWith<ResponseStatusException> {
            executor.execute(
                BatchSaveRequest(
                    nodes = BatchNodeOps(
                        create = listOf(
                            BatchNodeCreate(
                                tempId = "child",
                                name = "Child",
                                nodeTypeId = nodeType.id!!,
                                parentNodeId = foreignParent.id.toString()
                            )
                        )
                    )
                ),
                model,
                owner,
                Instant.now()
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Node ${foreignParent.id} does not belong to model ${model.id}", exception.reason)
    }

    @Test
    fun `update link from another model is rejected`() {
        val source = nodeOn(otherModel)
        val target = nodeOn(otherModel)
        val foreignLink = Links(
            id = UUID.randomUUID(),
            stableId = UUID.randomUUID(),
            source = source,
            target = target,
            owner = owner,
            linkType = linkType,
            model = otherModel,
            createdAt = Instant.now()
        )
        `when`(linksRepository.findById(foreignLink.id!!)).thenReturn(Optional.of(foreignLink))

        val exception = assertFailsWith<ResponseStatusException> {
            executor.execute(
                BatchSaveRequest(
                    links = BatchLinkOps(
                        update = listOf(
                            BatchLinkUpdate(
                                id = foreignLink.id!!,
                                sourceId = source.id.toString(),
                                targetId = target.id.toString(),
                                linkTypeId = linkType.id!!
                            )
                        )
                    )
                ),
                model,
                owner,
                Instant.now()
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Link ${foreignLink.id} does not belong to model ${model.id}", exception.reason)
    }

    @Test
    fun `update diagram from another model is rejected`() {
        val foreign = Diagrams(
            id = UUID.randomUUID(),
            name = "foreign",
            version = "1.0.0",
            owner = owner,
            model = otherModel,
            notation = notation,
            createdAt = Instant.now()
        )
        `when`(diagramsRepository.findById(foreign.id!!)).thenReturn(Optional.of(foreign))

        val exception = assertFailsWith<ResponseStatusException> {
            executor.execute(
                BatchSaveRequest(
                    diagrams = BatchDiagramOps(
                        update = listOf(
                            BatchDiagramUpdate(
                                id = foreign.id!!,
                                name = "foreign",
                                version = "1.0.0",
                                notationId = notation.id!!
                            )
                        )
                    )
                ),
                model,
                owner,
                Instant.now()
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Diagram ${foreign.id} does not belong to model ${model.id}", exception.reason)
    }

    @Test
    fun `delete diagram from another model is rejected`() {
        val foreign = Diagrams(
            id = UUID.randomUUID(),
            name = "foreign",
            version = "1.0.0",
            owner = owner,
            model = otherModel,
            notation = notation,
            createdAt = Instant.now()
        )
        `when`(diagramsRepository.findById(foreign.id!!)).thenReturn(Optional.of(foreign))

        val exception = assertFailsWith<ResponseStatusException> {
            executor.execute(
                BatchSaveRequest(
                    diagrams = BatchDiagramOps(
                        delete = listOf(BatchDeleteEntry(foreign.id!!))
                    )
                ),
                model,
                owner,
                Instant.now()
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Diagram ${foreign.id} does not belong to model ${model.id}", exception.reason)
    }

    private fun nodeOn(m: Models): Nodes =
        Nodes(
            id = UUID.randomUUID(),
            stableId = UUID.randomUUID(),
            name = "n",
            model = m,
            owner = owner,
            nodeType = nodeType,
            createdAt = Instant.now()
        )
}
