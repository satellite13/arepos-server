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

    @Test
    fun `findUnusedNodes returns orphans only`() {
        val model = persistModel()
        val owner = model.owner
        val notation = persistNotation(owner = owner)
        val nodeType = persistNodeType(owner = owner)
        val linkType = persistLinkType(owner = owner)

        val orphan = persistNode(model = model, owner = owner, nodeType = nodeType, name = "orphan")
        val folder = persistNode(model = model, owner = owner, nodeType = nodeType, name = "folder")
        persistNode(model = model, owner = owner, nodeType = nodeType, name = "child", parent = folder)
        val linked = persistNode(model = model, owner = owner, nodeType = nodeType, name = "linked")
        val linkedPeer = persistNode(model = model, owner = owner, nodeType = nodeType, name = "linked-peer")
        persistLink(model = model, owner = owner, linkType = linkType, source = linked, target = linkedPeer)
        val onDiagram = persistNode(model = model, owner = owner, nodeType = nodeType, name = "on-diagram")
        val boundNode = persistNode(model = model, owner = owner, nodeType = nodeType, name = "bound-node")
        val docNode = persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "doc-node",
            attrs = """{"label":"node","documentFileId":"f-1"}"""
        )
        persistDiagram(
            model = model,
            notation = notation,
            owner = owner,
            attrs = """{"instances":{"nodes":[{"id":"i1","modelNodeId":"${onDiagram.id}","x":0,"y":0}],"edges":[]}}"""
        )
        persistDiagram(model = model, notation = notation, owner = owner, node = boundNode)
        persistDiagram(
            model = model,
            notation = notation,
            owner = owner,
            attrs = """{"instances":{"nodes":[{"id":"i2","modelNodeId":"${docNode.id}","x":0,"y":0}],"edges":[]}}"""
        )

        val unused = nodesRepository.findUnusedNodes(model.id!!, 500)
        val names = unused.map { it.getName() }

        // orphan и child (лист, ни разу не размещённый на диаграммах) — сироты.
        assertTrue("orphan" in names)
        assertTrue("child" in names)
        assertFalse("folder" in names)
        assertFalse("linked" in names)
        assertFalse("linked-peer" in names)
        assertFalse("on-diagram" in names)
        assertFalse("bound-node" in names)
        assertFalse("doc-node" in names)
        assertEquals(2, nodesRepository.countUnusedNodes(model.id!!))
        val row = unused.single { it.getName() == "orphan" }
        assertEquals(orphan.id, row.getId())

        // Повторная проверка id перед удалением.
        assertEquals(
            listOf(orphan.id),
            nodesRepository.findUnusedNodeIds(model.id!!, listOfNotNull(orphan.id, onDiagram.id))
        )
    }

    @Test
    fun `findUnusedLinks returns links without diagram references only`() {
        val model = persistModel()
        val owner = model.owner
        val notation = persistNotation(owner = owner)
        val nodeType = persistNodeType(owner = owner)
        val linkType = persistLinkType(owner = owner)
        val a = persistNode(model = model, owner = owner, nodeType = nodeType, name = "a")
        val b = persistNode(model = model, owner = owner, nodeType = nodeType, name = "b")
        val c = persistNode(model = model, owner = owner, nodeType = nodeType, name = "c")
        val unusedLink = persistLink(model = model, owner = owner, linkType = linkType, source = a, target = b)
        val usedLink = persistLink(model = model, owner = owner, linkType = linkType, source = b, target = c)
        persistDiagram(
            model = model,
            notation = notation,
            owner = owner,
            attrs = """{"instances":{"nodes":[],"edges":[{"id":"e1","modelLinkId":"${usedLink.id}"}]}}"""
        )

        val unused = linksRepository.findUnusedLinks(model.id!!, 500)

        assertEquals(listOf(unusedLink.id), unused.map { it.getId() })
        assertEquals(1, linksRepository.countUnusedLinks(model.id!!))
        assertEquals(
            listOf(unusedLink.id),
            linksRepository.findUnusedLinkIds(model.id!!, listOfNotNull(unusedLink.id, usedLink.id))
        )
    }

    @Test
    fun `findDuplicateNodeMembers excludes dpc-autocreated nodes`() {
        val model = persistModel()
        val owner = model.owner
        val nodeType = persistNodeType(owner = owner)

        // Обычные узлы с одним именем → дубликат.
        persistNode(model = model, owner = owner, nodeType = nodeType, name = "iface")
        persistNode(model = model, owner = owner, nodeType = nodeType, name = "iface")
        // Разные версии одного интерфейса от org-tree-sync → исключены.
        val dpcAttrs = """{"dpc":{"kind":"appApi","id":"api-1","autoCreated":true}}"""
        persistNode(model = model, owner = owner, nodeType = nodeType, name = "orders-api", attrs = dpcAttrs)
        persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "orders-api",
            attrs = """{"dpc":{"kind":"appApi","id":"api-2","autoCreated":true}}"""
        )
        // Смесь: обычный + dpc с одним именем → группа не образуется.
        persistNode(model = model, owner = owner, nodeType = nodeType, name = "mixed")
        persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "mixed",
            attrs = """{"dpc":{"kind":"appComponent","id":"c-1","autoCreated":true}}"""
        )

        val rows = nodesRepository.findDuplicateNodeMembers(model.id!!, 200, 200)

        assertEquals(1, rows.firstOrNull()?.getTotalGroups())
        assertEquals(2, rows.count())
        assertTrue(rows.all { it.getName() == "iface" })
    }

    @Test
    fun `findDuplicateNodeMembers treats same-name nodes with different versions as distinct`() {
        val model = persistModel()
        val owner = model.owner
        val nodeType = persistNodeType(owner = owner)

        persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "api-v1",
            attrs = """{"typeProperties":{"version":"1.0.1"}}"""
        )
        persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "api-v1",
            attrs = """{"typeProperties":{"version":"1.2.1"}}"""
        )
        // Та же версия дважды → настоящий дубликат.
        persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "api-v2",
            attrs = """{"typeProperties":{"version":"2.0.0"}}"""
        )
        persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "api-v2",
            attrs = """{"typeProperties":{"version":"2.0.0"}}"""
        )

        val rows = nodesRepository.findDuplicateNodeMembers(model.id!!, 200, 200)

        assertEquals(1, rows.firstOrNull()?.getTotalGroups())
        assertEquals(2, rows.count())
        assertTrue(rows.all { it.getName() == "api-v2" })
    }

    @Test
    fun `findUnusedNodes excludes dpc-autocreated orphans`() {
        val model = persistModel()
        val owner = model.owner
        val nodeType = persistNodeType(owner = owner)
        persistNode(model = model, owner = owner, nodeType = nodeType, name = "plain-orphan")
        persistNode(
            model = model,
            owner = owner,
            nodeType = nodeType,
            name = "synced-orphan",
            attrs = """{"dpc":{"kind":"appApi","id":"api-9","autoCreated":true}}"""
        )

        val names = nodesRepository.findUnusedNodes(model.id!!, 500).map { it.getName() }

        assertTrue("plain-orphan" in names)
        assertFalse("synced-orphan" in names)
    }

    @Test
    fun `findUnusedLinks excludes dpc links`() {
        val model = persistModel()
        val owner = model.owner
        val nodeType = persistNodeType(owner = owner)
        val linkType = persistLinkType(owner = owner)
        val a = persistNode(model = model, owner = owner, nodeType = nodeType, name = "a")
        val b = persistNode(model = model, owner = owner, nodeType = nodeType, name = "b")
        persistLink(model = model, owner = owner, linkType = linkType, source = a, target = b)
        persistLink(
            model = model,
            owner = owner,
            linkType = linkType,
            source = a,
            target = b,
            attrs = """{"dpc":{"kind":"dpcLink","id":"P1->C1"}}"""
        )

        assertEquals(1, linksRepository.countUnusedLinks(model.id!!))
    }
}

