package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NodeTypesRepositoryTest : RepositoryTestBase() {

    @Test
    fun `persists node type`() {
        val nodeType = persistNodeType()
        val found = nodeTypesRepository.findById(nodeType.id!!)
        assertTrue(found.isPresent)
        assertEquals(nodeType.name, found.get().name)
    }
}

