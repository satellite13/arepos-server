package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ComponentsRepositoryTest : RepositoryTestBase() {

    @Test
    fun `persists component bound to notation`() {
        val component = persistComponent()
        val found = componentsRepository.findById(component.id!!)
        assertTrue(found.isPresent)
        assertEquals(component.notation.id, found.get().notation.id)
    }

    @Test
    fun `findByFilters filters by tags jsonb contains all`() {
        val owner = persistUser()
        val notation = persistNotation(owner = owner)
        val nodeType = persistNodeType(owner = owner)

        persistComponent(
            notation = notation,
            owner = owner,
            nodeType = nodeType,
            name = "tagged-hit",
            attrs = """{"tags":["alpha","beta"]}"""
        )
        persistComponent(
            notation = notation,
            owner = owner,
            nodeType = nodeType,
            name = "tagged-miss",
            attrs = """{"tags":["alpha"]}"""
        )

        val result = componentsRepository.findByFilters(
            notationId = notation.id,
            ownerId = null,
            name = null,
            tagsJson = """["alpha","beta"]""",
            pageable = Pageable.unpaged()
        )

        assertEquals(1, result.totalElements)
        assertEquals("tagged-hit", result.content.first().name)
    }
}

