package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Nodes
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.DiagramsRepository
import ru.kavader.arepos.repository.LinksRepository
import ru.kavader.arepos.repository.ModelsRepository
import ru.kavader.arepos.repository.NodesRepository
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class ModelDiffServiceTest {
    @Mock
    lateinit var modelsRepository: ModelsRepository

    @Mock
    lateinit var nodesRepository: NodesRepository

    @Mock
    lateinit var linksRepository: LinksRepository

    @Mock
    lateinit var diagramsRepository: DiagramsRepository

    private fun newService(): ModelDiffService =
        ModelDiffService(modelsRepository, nodesRepository, linksRepository, diagramsRepository)

    private val owner = Users(
        id = UUID.randomUUID(),
        email = "owner@test.com",
        createdAt = Instant.now()
    )

    private val model = Models(
        id = UUID.randomUUID(),
        name = "m",
        createdAt = Instant.now(),
        version = "1.0.0",
        owner = owner
    )

    private val nodeType = NodeTypes(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        name = "Type",
        owner = owner
    )

    @Test
    fun `buildNodePathMap builds hierarchical paths`() {
        val rootId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val grandChildId = UUID.randomUUID()

        val nodes = listOf(
            node(rootId, "Root", null),
            node(childId, "Child", rootId),
            node(grandChildId, "Leaf", childId)
        )

        val result = newService().buildNodePathMap(nodes)

        assertEquals("Root", result[rootId])
        assertEquals("Root/Child", result[childId])
        assertEquals("Root/Child/Leaf", result[grandChildId])
    }

    @Test
    fun `buildNodePathMap rejects cycles`() {
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val nodes = listOf(
            node(firstId, "A", secondId),
            node(secondId, "B", firstId)
        )

        val ex = assertFailsWith<ResponseStatusException> {
            newService().buildNodePathMap(nodes)
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.statusCode)
    }

    @Test
    fun `buildNodePathMap rejects excessive depth`() {
        val chain = mutableListOf<Nodes>()
        var parentId: UUID? = null
        repeat(257) { idx ->
            val id = UUID.randomUUID()
            chain += node(id, "n$idx", parentId)
            parentId = id
        }

        val ex = assertFailsWith<ResponseStatusException> {
            newService().buildNodePathMap(chain)
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.statusCode)
    }

    private fun node(id: UUID, name: String, parentId: UUID?): Nodes {
        val parentRef = parentId?.let { pid ->
            Nodes(
                id = pid,
                stableId = UUID.randomUUID(),
                name = "p",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                model = model,
                owner = owner,
                nodeType = nodeType
            )
        }
        return Nodes(
            id = id,
            stableId = UUID.randomUUID(),
            name = name,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            parentNode = parentRef,
            model = model,
            owner = owner,
            nodeType = nodeType
        )
    }
}
