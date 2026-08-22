package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `direct children page applies tree filters order totals and paging`() {
        val model = persistModel()
        val owner = model.owner
        val directoryType = persistNodeType(owner = owner, name = "Directory")
        val regularType = persistNodeType(owner = owner, name = "Application")
        val parent = persistNode(model = model, owner = owner, nodeType = directoryType)
        val tiedHigh = persistNode(
            model = model,
            owner = owner,
            nodeType = directoryType,
            name = "tied-high",
            attrs = """{"treeOrder":1}""",
            parent = parent
        )
        val tiedLow = persistNode(
            model = model,
            owner = owner,
            nodeType = directoryType,
            name = "tied-low",
            attrs = """{"treeOrder":1}""",
            parent = parent
        )
        val regular = persistNode(
            model = model,
            owner = owner,
            nodeType = regularType,
            attrs = """{"treeOrder":2}""",
            parent = parent
        )
        persistNode(
            model = model,
            owner = owner,
            nodeType = directoryType,
            attrs = """{"treeOrder":0,"system":{"hiddenTreeRoot":true}}""",
            parent = parent
        )
        persistNode(
            model = model,
            owner = owner,
            nodeType = regularType,
            attrs = """{"system":{"hiddenTreeRoot":true}}""",
            parent = tiedLow
        )
        persistNode(model = model, owner = owner, nodeType = directoryType, parent = tiedHigh)
        persistNode(model = model, owner = owner, nodeType = regularType, parent = regular)
        val tiedIds = listOf(tiedLow.id!!, tiedHigh.id!!).sortedBy { it.toString() }

        val page0 = nodesRepository.findDirectChildrenPage(
            model.id!!,
            parent.id,
            excludeSystem = true,
            foldersOnly = false,
            PageRequest.of(0, 2)
        )
        val page1 = nodesRepository.findDirectChildrenPage(
            model.id!!,
            parent.id,
            excludeSystem = true,
            foldersOnly = false,
            PageRequest.of(1, 2)
        )
        val folders = nodesRepository.findDirectChildrenPage(
            model.id!!,
            parent.id,
            excludeSystem = true,
            foldersOnly = true,
            Pageable.unpaged()
        )
        val includingSystem = nodesRepository.findDirectChildrenPage(
            model.id!!,
            parent.id,
            excludeSystem = false,
            foldersOnly = false,
            Pageable.unpaged()
        )

        assertEquals(3, page0.totalElements)
        assertEquals(4, includingSystem.totalElements)
        assertEquals(tiedIds, page0.content.map { it.getId() })
        assertEquals(listOf(regular.id), page1.content.map { it.getId() })
        val unrestrictedHasChildren = page0.content.associate { it.getId() to it.getHasChildren() }
        assertFalse(unrestrictedHasChildren.getValue(tiedLow.id!!))
        assertTrue(unrestrictedHasChildren.getValue(tiedHigh.id!!))
        assertEquals(tiedIds, folders.content.map { it.getId() })
        val folderHasChildren = folders.content.associate { it.getId() to it.getHasChildren() }
        assertFalse(folderHasChildren.getValue(tiedLow.id!!))
        assertTrue(folderHasChildren.getValue(tiedHigh.id!!))
    }

    @Test
    fun `folders only hasChildren ignores visible non-directory children`() {
        val model = persistModel()
        val owner = model.owner
        val directoryType = persistNodeType(owner = owner, name = "Directory")
        val regularType = persistNodeType(owner = owner, name = "Application")
        val root = persistNode(model = model, owner = owner, nodeType = directoryType)
        val folder = persistNode(model = model, owner = owner, nodeType = directoryType, parent = root)
        persistNode(model = model, owner = owner, nodeType = regularType, parent = folder)

        val allChildren = nodesRepository.findDirectChildrenPage(
            model.id!!,
            root.id,
            excludeSystem = true,
            foldersOnly = false,
            Pageable.unpaged()
        )
        val foldersOnly = nodesRepository.findDirectChildrenPage(
            model.id!!,
            root.id,
            excludeSystem = true,
            foldersOnly = true,
            Pageable.unpaged()
        )

        assertTrue(allChildren.content.single().getHasChildren())
        assertFalse(foldersOnly.content.single().getHasChildren())
    }
}

