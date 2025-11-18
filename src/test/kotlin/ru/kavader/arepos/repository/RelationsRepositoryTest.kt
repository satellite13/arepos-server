package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
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
}

