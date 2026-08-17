package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.kavader.arepos.repository.RepositoryTestBase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class DiagramOnlyOrphanCleanupServiceTest : RepositoryTestBase() {

    @Autowired
    lateinit var diagramLifecycleService: DiagramLifecycleService

    @Test
    fun `deleting a diagram removes hidden Diagram only nodes unused elsewhere`() {
        val owner = persistUser(email = "diagram-only-orphan@test.com")
        val notation = persistNotation(owner = owner, name = "Route-orphan", version = "1.0.0")
        val diagramOnlyType = persistNodeType(owner = owner, name = "Diagram only", attrs = "{}")
        val actorType = persistNodeType(owner = owner, name = "Business Actor")
        persistComponent(notation = notation, nodeType = diagramOnlyType, owner = owner, name = "Simple Note")

        val model = persistModel(owner = owner, name = "Orphan cleanup model", version = "1.1.0")
        val leftover = persistNode(model = model, owner = owner, nodeType = diagramOnlyType, name = "Simple path")
        val actor = persistNode(model = model, owner = owner, nodeType = actorType, name = "Actor")
        val diagram = persistDiagram(
            model = model,
            notation = notation,
            owner = owner,
            name = "Route to Work",
            attrs = """{"instances":{"nodes":[{"id":"inst-1","modelNodeId":"${leftover.id}"}],"edges":[]}}"""
        )

        diagramLifecycleService.softDeleteDiagram(diagram)

        assertFalse(nodesRepository.existsById(leftover.id!!))
        assertTrue(nodesRepository.existsById(actor.id!!))
    }

    @Test
    fun `keeps Diagram only node still placed on another live diagram`() {
        val owner = persistUser(email = "diagram-only-shared@test.com")
        val notation = persistNotation(owner = owner, name = "Route-shared", version = "1.0.0")
        val diagramOnlyType = persistNodeType(owner = owner, name = "Diagram only", attrs = "{}")
        persistComponent(notation = notation, nodeType = diagramOnlyType, owner = owner, name = "Simple Note")

        val model = persistModel(owner = owner, name = "Shared", version = "1.0.0")
        val shared = persistNode(model = model, owner = owner, nodeType = diagramOnlyType, name = "Note")
        val first = persistDiagram(
            model = model,
            notation = notation,
            owner = owner,
            name = "First",
            attrs = """{"instances":{"nodes":[{"id":"a","modelNodeId":"${shared.id}"}],"edges":[]}}"""
        )
        persistDiagram(
            model = model,
            notation = notation,
            owner = owner,
            name = "Second",
            attrs = """{"instances":{"nodes":[{"id":"b","modelNodeId":"${shared.id}"}],"edges":[]}}"""
        )

        diagramLifecycleService.softDeleteDiagram(first)

        assertTrue(nodesRepository.existsById(shared.id!!))
    }
}
