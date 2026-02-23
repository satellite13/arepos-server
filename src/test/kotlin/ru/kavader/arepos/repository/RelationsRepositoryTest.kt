package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RelationsRepositoryTest : RepositoryTestBase() {

    @Test
    fun `persists relation referencing notation and link type`() {
        val relation = persistRelation()
        val found = relationsRepository.findById(relation.id!!)
        assertTrue(found.isPresent)
        assertEquals(relation.linkType.id, found.get().linkType.id)
    }

    @Test
    fun `findByFilters filters by tags jsonb contains all`() {
        val owner = persistUser()
        val notation = persistNotation(owner = owner)
        val linkType = persistLinkType(owner = owner)

        persistRelation(
            notation = notation,
            owner = owner,
            linkType = linkType,
            name = "relation-hit",
            attrs = """{"tags":["alpha","beta"]}"""
        )
        persistRelation(
            notation = notation,
            owner = owner,
            linkType = linkType,
            name = "relation-miss",
            attrs = """{"tags":["alpha"]}"""
        )

        val result = relationsRepository.findByFilters(
            notationId = notation.id,
            ownerId = null,
            name = null,
            tagsJson = """["alpha","beta"]""",
            pageable = Pageable.unpaged()
        )

        assertEquals(1, result.totalElements)
        assertEquals("relation-hit", result.content.first().name)
    }
}

