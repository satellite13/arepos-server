package ru.kavader.arepos.service.modelbatch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import ru.kavader.arepos.dto.model.BatchNodeOps
import ru.kavader.arepos.dto.model.BatchNodeUpdate
import ru.kavader.arepos.dto.model.BatchSaveRequest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.NodesRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class BatchConflictCollectorTest {

    @Mock
    lateinit var nodesRepository: NodesRepository

    @Mock
    lateinit var linksRepository: LinksRepository

    @Mock
    lateinit var diagramsRepository: DiagramsRepository

    @Test
    fun `null baseUpdatedAt is treated as conflict`() {
        val collector = BatchConflictCollector(nodesRepository, linksRepository, diagramsRepository)
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com")
        val model = Models(
            id = UUID.randomUUID(),
            name = "m",
            version = "1.0.0",
            owner = owner,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val nodeType = NodeTypes(
            id = UUID.randomUUID(),
            name = "t",
            owner = owner,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val nodeId = UUID.randomUUID()
        val serverUpdated = Instant.parse("2026-01-02T00:00:00Z")
        val node = Nodes(
            id = nodeId,
            stableId = UUID.randomUUID(),
            name = "n",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = serverUpdated,
            model = model,
            owner = owner,
            nodeType = nodeType
        )
        `when`(nodesRepository.findById(nodeId)).thenReturn(Optional.of(node))

        val conflicts = collector.collect(
            BatchSaveRequest(
                nodes = BatchNodeOps(
                    update = listOf(
                        BatchNodeUpdate(
                            id = nodeId,
                            name = "n2",
                            nodeTypeId = nodeType.id!!,
                            baseUpdatedAt = null
                        )
                    )
                )
            ),
            model
        )

        assertEquals(1, conflicts.size)
        assertEquals("node", conflicts[0].kind)
        assertEquals(nodeId, conflicts[0].id)
        assertEquals(serverUpdated, conflicts[0].serverUpdatedAt)
        assertNull(conflicts[0].clientBaseUpdatedAt)
    }

    @Test
    fun `force true returns empty conflicts even with null baseUpdatedAt`() {
        val collector = BatchConflictCollector(nodesRepository, linksRepository, diagramsRepository)
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com")
        val model = Models(
            id = UUID.randomUUID(),
            name = "m",
            version = "1.0.0",
            owner = owner,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val conflicts = collector.collect(
            BatchSaveRequest(
                force = true,
                nodes = BatchNodeOps(
                    update = listOf(
                        BatchNodeUpdate(
                            id = UUID.randomUUID(),
                            name = "n2",
                            nodeTypeId = UUID.randomUUID(),
                            baseUpdatedAt = null
                        )
                    )
                )
            ),
            model
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `wrong-model entity is skipped even when baseUpdatedAt is null`() {
        val collector = BatchConflictCollector(nodesRepository, linksRepository, diagramsRepository)
        val owner = Users(id = UUID.randomUUID(), email = "owner@test.com")
        val model = Models(
            id = UUID.randomUUID(),
            name = "m",
            version = "1.0.0",
            owner = owner,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val otherModel = Models(
            id = UUID.randomUUID(),
            name = "other",
            version = "1.0.0",
            owner = owner,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val nodeType = NodeTypes(
            id = UUID.randomUUID(),
            name = "t",
            owner = owner,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val nodeId = UUID.randomUUID()
        val node = Nodes(
            id = nodeId,
            stableId = UUID.randomUUID(),
            name = "n",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            model = otherModel,
            owner = owner,
            nodeType = nodeType
        )
        `when`(nodesRepository.findById(nodeId)).thenReturn(Optional.of(node))

        val conflicts = collector.collect(
            BatchSaveRequest(
                nodes = BatchNodeOps(
                    update = listOf(
                        BatchNodeUpdate(
                            id = nodeId,
                            name = "n2",
                            nodeTypeId = nodeType.id!!,
                            baseUpdatedAt = null
                        )
                    )
                )
            ),
            model
        )
        assertTrue(conflicts.isEmpty())
    }
}
