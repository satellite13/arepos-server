package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LinksRepositoryTest : RepositoryTestBase() {

    @Test
    fun `creates link between nodes of same model`() {
        val link = persistLink()
        val found = linksRepository.findById(link.id!!)
        assertTrue(found.isPresent)
        assertEquals(link.model.id, found.get().model.id)
    }
}

