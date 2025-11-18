package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LinkTypesRepositoryTest : RepositoryTestBase() {

    @Test
    fun `saves link type`() {
        val linkType = persistLinkType()
        val found = linkTypesRepository.findById(linkType.id!!)
        assertTrue(found.isPresent)
        assertEquals(linkType.name, found.get().name)
    }
}

