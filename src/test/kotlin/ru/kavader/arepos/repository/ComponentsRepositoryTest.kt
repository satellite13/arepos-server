package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
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
}

