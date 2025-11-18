package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
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
}

