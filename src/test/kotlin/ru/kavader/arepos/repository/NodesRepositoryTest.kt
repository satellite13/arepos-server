package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NodesRepositoryTest : RepositoryTestBase() {

    @Test
    fun `persists child node with parent`() {
        val model = persistModel()
        val owner = model.owner
        val nodeType = persistNodeType(owner = owner)
        val parentNode = persistNode(model = model, owner = owner, nodeType = nodeType)
        val childNode = persistNode(model = model, owner = owner, nodeType = nodeType, parent = parentNode)

        val found = nodesRepository.findById(childNode.id!!)
        assertTrue(found.isPresent)
        assertEquals(parentNode.id, found.get().parentNode?.id)
    }

    @Test
    fun `findByModelIdOrdered sorts by parent then treeOrder`() {
        val model = persistModel()
        val owner = model.owner
        val nodeType = persistNodeType(owner = owner)

        val rootHigh = persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "root-high",
            attrs = """{"treeOrder":2}"""
        )
        val rootLow = persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "root-low",
            attrs = """{"treeOrder":1}"""
        )
        val childHigh = persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "child-high",
            attrs = """{"treeOrder":2}""",
            parent = rootLow
        )
        val childLow = persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "child-low",
            attrs = """{"treeOrder":1}""",
            parent = rootLow
        )

        val ordered = nodesRepository.findByModelIdOrdered(model.id!!, Pageable.unpaged()).content

        assertEquals(listOf(rootLow.id, rootHigh.id, childLow.id, childHigh.id), ordered.map { it.id })
    }
}

