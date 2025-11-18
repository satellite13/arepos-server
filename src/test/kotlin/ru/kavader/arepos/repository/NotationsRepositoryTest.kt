package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotationsRepositoryTest : RepositoryTestBase() {

    @Test
    fun `saves notation with owner`() {
        val notation = persistNotation()
        val found = notationsRepository.findById(notation.id!!)
        assertTrue(found.isPresent)
        assertEquals(notation.owner.id, found.get().owner.id)
    }
}

