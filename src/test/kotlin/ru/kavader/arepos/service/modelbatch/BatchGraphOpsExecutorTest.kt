package ru.kavader.arepos.service.modelbatch

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.model.BatchLinkCreate
import ru.kavader.arepos.dto.model.BatchLinkOps
import ru.kavader.arepos.dto.model.BatchNodeCreate
import ru.kavader.arepos.dto.model.BatchNodeOps
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.model.Models
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
}
