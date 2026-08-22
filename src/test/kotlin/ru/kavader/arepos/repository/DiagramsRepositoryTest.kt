package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.Query
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DiagramsRepositoryTest : RepositoryTestBase() {

    @ParameterizedTest
    @ValueSource(strings = ["01.0.0", "1.01.0", "1.0.01", "1.0.0-alpha.01"])
    fun `database rejects invalid storage SemVer versions`(version: String) {
        val model = persistModel()
        val notation = persistNotation(owner = model.owner)

        assertFailsWith<Exception> {
            diagramsRepository.saveAndFlush(
                persistDiagram(model = model, notation = notation, version = version)
            )
        }
    }

    @Test
    fun `database accepts valid storage SemVer versions`() {
        val model = persistModel()
        val notation = persistNotation(owner = model.owner)

        listOf("0.0.0", "1.0.0-alpha.1", "1.0.0-alpha.beta").forEachIndexed { index, version ->
            diagramsRepository.saveAndFlush(
                persistDiagram(model = model, notation = notation, name = "valid-$index", version = version)
            )
        }
    }

    @Test
    fun `findAll returns only not deleted diagrams`() {
        val model = persistModel()
        val notation = persistNotation(owner = model.owner)
        persistDiagram(model = model, notation = notation, name = "active-diagram", version = "1.0.0")
        val deletedDiagram =
            persistDiagram(model = model, notation = notation, name = "deleted-diagram", version = "1.0.1")
        deletedDiagram.deleted = true
        diagramsRepository.save(deletedDiagram)

        val diagrams = diagramsRepository.findByFilters(
            ownerId = null,
            modelId = model.id,
            nodeId = null,
            notationId = null,
            name = "",
            pageable = org.springframework.data.domain.Pageable.unpaged()
        )
        assertEquals(1, diagrams.totalElements)
        assertEquals("active-diagram", diagrams.content.first().name)
    }

    @Test
    fun `soft delete hides diagram from repository queries`() {
        val diagram = persistDiagram()
        assertTrue(diagramsRepository.existsById(diagram.id!!))

        val deletedCount = diagramsRepository.softDeleteById(diagram.id!!)
        assertEquals(1, deletedCount)
        assertFalse(diagramsRepository.existsById(diagram.id!!))
        assertTrue(diagramsRepository.findById(diagram.id!!).isEmpty)
    }

    @Test
    fun `checks uniqueness by model name version`() {
        val model = persistModel()
        val notation = persistNotation(owner = model.owner)
        val first = persistDiagram(
            model = model,
            notation = notation,
            name = "diagram-unique",
            version = "2.1.0"
        )

        assertTrue(diagramsRepository.existsByModelAndNameAndVersion(model, "diagram-unique", "2.1.0"))
        assertFalse(
            diagramsRepository.existsByModelAndNameAndVersionAndIdNot(
                model,
                "diagram-unique",
                "2.1.0",
                first.id!!
            )
        )

        first.deleted = true
        diagramsRepository.saveAndFlush(first)
        assertTrue(diagramsRepository.existsByModelAndNameAndVersion(model, "diagram-unique", "2.1.0"))
    }

    @Test
    fun `existsViewableModelDiagramWithNotation true when model shared VIEW and notation not shared`() {
        val modelOwner = persistUser()
        val notationOwner = persistUser()
        val viewer = persistUser()
        val notation = persistNotation(owner = notationOwner)
        val model = persistModel(owner = modelOwner)
        persistDiagram(model = model, notation = notation, owner = modelOwner)
        val now = Instant.now()
        resourceSharesRepository.save(
            ResourceShares(
                resourceType = ShareResourceType.MODEL,
                resourceId = model.id!!,
                granteeUser = null,
                grantedByUser = modelOwner,
                permission = SharePermission.VIEW,
                createdAt = now
            )
        )
        assertTrue(
            diagramsRepository.existsViewableModelDiagramWithNotation(notation.id!!, viewer.id!!)
        )
    }

    @Test
    fun `pages slim exact-path active diagram references by name and id`() {
        val owner = persistUser()
        val model = persistModel(owner)
        val otherModel = persistModel(owner)
        val notation = persistNotation(owner)
        val target = persistNode(model = model, owner = owner)
        val diagramNode = persistNode(model = model, owner = owner)
        val attrs = referenceAttrs(target.id!!)
        val beta = persistDiagram(model, notation, owner, diagramNode, "Beta", "1.0.0", attrs)
        val alpha = persistDiagram(model, notation, owner, null, "Alpha", "1.0.0", attrs)
        persistDiagram(
            model,
            notation,
            owner,
            null,
            "Nested",
            "1.0.0",
            """{"instances":{"nodes":[{"nested":{"modelNodeId":"${target.id}"}}]}}"""
        )
        persistDiagram(
            model,
            notation,
            owner,
            null,
            "Wrong array",
            "1.0.0",
            """{"instances":{"links":[{"modelNodeId":"${target.id}"}]}}"""
        )
        val deleted = persistDiagram(model, notation, owner, null, "Deleted", "1.0.0", attrs)
        deleted.deleted = true
        diagramsRepository.save(deleted)
        persistDiagram(otherModel, notation, owner, null, "Foreign", "1.0.0", attrs)

        val firstPage = diagramsRepository.findDiagramReferences(
            model.id!!,
            diagramReferenceJsonPath(target.id!!),
            PageRequest.of(0, 1)
        )
        val secondPage = diagramsRepository.findDiagramReferences(
            model.id!!,
            diagramReferenceJsonPath(target.id!!),
            PageRequest.of(1, 1)
        )

        assertEquals(2, firstPage.totalElements)
        assertEquals(alpha.id, firstPage.content.single().getId())
        assertEquals(beta.id, secondPage.content.single().getId())
        assertEquals(diagramNode.id, secondPage.content.single().getNodeId())
        assertEquals(notation.id, secondPage.content.single().getNotationId())
    }

    @Test
    fun `pages latest active diagram projection with prerelease semver precedence`() {
        val owner = persistUser()
        val model = persistModel(owner)
        val notation = persistNotation(owner)
        persistDiagram(model, notation, owner, null, "Match Alpha", "1.0.0")
        persistDiagram(model, notation, owner, null, "Match Alpha", "1.1.0-alpha")
        val latest = persistDiagram(model, notation, owner, null, "Match Alpha", "1.1.0-alpha.1")

        val page = diagramsRepository.searchLatestActiveByModelIdAndName(
            model.id!!,
            "match",
            PageRequest.of(0, 1)
        )

        assertEquals(1, page.totalElements)
        assertEquals(1, page.content.size)
        assertEquals(latest.id, page.content.single().getId())
        assertEquals("1.1.0-alpha.1", page.content.single().getVersion())
        assertEquals(notation.name, page.content.single().getNotationName())
        assertEquals(latest.node?.id, page.content.single().getNodeId())
    }

    @Test
    fun `latest diagram projection paginates distinct names in the database`() {
        val owner = persistUser()
        val model = persistModel(owner)
        val notation = persistNotation(owner)
        persistDiagram(model, notation, owner, null, "Match Alpha", "1.0.0")
        val alphaLatest = persistDiagram(model, notation, owner, null, "Match Alpha", "1.1.0")
        val beta = persistDiagram(model, notation, owner, null, "Match Beta", "1.0.0")

        val firstPage = diagramsRepository.searchLatestActiveByModelIdAndName(
            model.id!!,
            "match",
            PageRequest.of(0, 1)
        )
        val secondPage = diagramsRepository.searchLatestActiveByModelIdAndName(
            model.id!!,
            "match",
            PageRequest.of(1, 1)
        )

        assertEquals(2, firstPage.totalElements)
        assertEquals(1, firstPage.content.size)
        assertEquals(alphaLatest.id, firstPage.content.single().getId())
        assertEquals(2, secondPage.totalElements)
        assertEquals(1, secondPage.content.size)
        assertEquals(beta.id, secondPage.content.single().getId())
    }

    @Test
    fun `latest diagram projection follows prerelease identifier precedence`() {
        val owner = persistUser()
        val model = persistModel(owner)
        val notation = persistNotation(owner)
        persistDiagram(model, notation, owner, null, "Match Semver Numeric", "1.1.0-alpha.2")
        val alphaTen = persistDiagram(model, notation, owner, null, "Match Semver Numeric", "1.1.0-alpha.10")
        persistDiagram(model, notation, owner, null, "Match Semver Text", "1.1.0-alpha.1")
        val alphaBeta = persistDiagram(model, notation, owner, null, "Match Semver Text", "1.1.0-alpha.beta")

        val page = diagramsRepository.searchLatestActiveByModelIdAndName(
            model.id!!,
            "match semver",
            PageRequest.of(0, 10)
        )

        assertEquals(2, page.totalElements)
        assertEquals(alphaTen.id, page.content[0].getId())
        assertEquals(alphaBeta.id, page.content[1].getId())
    }

    @Test
    fun `diagram reference query uses the indexed jsonpath operator`() {
        val method = DiagramsRepository::class.java.getMethod(
            "findDiagramReferences",
            UUID::class.java,
            String::class.java,
            org.springframework.data.domain.Pageable::class.java
        )
        val query = method.getAnnotation(Query::class.java)

        assertContains(query.value, "d.attrs @@ CAST(:nodeJsonPath AS jsonpath)")
        assertContains(query.countQuery, "d.attrs @@ CAST(:nodeJsonPath AS jsonpath)")
    }

    private fun referenceAttrs(nodeId: UUID): String =
        """{"instances":{"nodes":[{"modelNodeId":"$nodeId"}]}}"""

    private fun diagramReferenceJsonPath(nodeId: UUID): String =
        """exists($.instances.nodes[*] ? (@.modelNodeId == "$nodeId"))"""
}
