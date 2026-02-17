package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DiagramsRepositoryTest : RepositoryTestBase() {

    @Test
    fun `findAll returns only not deleted diagrams`() {
        persistDiagram(name = "active-diagram", version = "1.0.0")
        val deletedDiagram = persistDiagram(name = "deleted-diagram", version = "1.0.1")
        diagramsRepository.save(deletedDiagram.copy(deleted = true))

        val diagrams = diagramsRepository.findAll(org.springframework.data.domain.Pageable.unpaged())
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
        assertFalse(diagramsRepository.existsByModelAndNameAndVersionAndIdNot(model, "diagram-unique", "2.1.0", first.id!!))
    }
}
