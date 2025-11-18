package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RelationRulesRepositoryTest : RepositoryTestBase() {

    @Test
    fun `persists relation rule for components`() {
        val rule = persistRelationRule()

        val found = relationRulesRepository.findById(rule.id!!)
        assertTrue(found.isPresent)
        assertEquals(rule.relation.id, found.get().relation.id)
    }
}

