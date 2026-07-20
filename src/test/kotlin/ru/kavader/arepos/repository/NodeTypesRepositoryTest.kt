package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import ru.kavader.arepos.model.NodeTypes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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

    @Test
    fun `allows same node type name for different owners`() {
        val ownerA = persistUser("owner-a-nt@example.com")
        val ownerB = persistUser("owner-b-nt@example.com")
        val typeA = persistNodeType(owner = ownerA, name = "Application Function")
        val typeB = persistNodeType(owner = ownerB, name = "Application Function")

        assertNotEquals(typeA.id, typeB.id)
        assertEquals(typeA.name, typeB.name)
        assertEquals(typeA.id, nodeTypesRepository.findByOwnerAndNameIgnoreCase(ownerA, "application function")?.id)
        assertEquals(typeB.id, nodeTypesRepository.findByOwnerAndNameIgnoreCase(ownerB, "APPLICATION FUNCTION")?.id)
        assertNull(nodeTypesRepository.findByOwnerAndNameIgnoreCase(ownerA, "Missing Type"))
    }

    @Test
    fun `rejects duplicate node type name for same owner ignoring case`() {
        val owner = persistUser("owner-dup-nt@example.com")
        persistNodeType(owner = owner, name = "Application Function")

        // Constraint fires as JDBC error; Spring may wrap it as JpaSystemException in @DataJpaTest.
        assertThrows<Exception> {
            nodeTypesRepository.saveAndFlush(
                NodeTypes(
                    name = "application function",
                    attrs = null,
                    createdAt = Instant.now(),
                    owner = owner
                )
            )
        }
    }
}

