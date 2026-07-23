package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import ru.kavader.arepos.model.ResourceShares
import ru.kavader.arepos.model.SharePermission
import ru.kavader.arepos.model.ShareResourceType
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DiagramsRepositoryTest : RepositoryTestBase() {

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
}
