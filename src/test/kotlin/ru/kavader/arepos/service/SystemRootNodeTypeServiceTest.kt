package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.kavader.arepos.repository.RepositoryTestBase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
class SystemRootNodeTypeServiceTest : RepositoryTestBase() {

    @Autowired
    lateinit var systemRootNodeTypeService: SystemRootNodeTypeService

    @Test
    fun `getOrCreate reuses seeded system Directory instead of creating per owner`() {
        val systemDirectory = nodeTypesRepository.findByOwnerEmailIgnoreCaseAndNameIgnoreCase(
            ownerEmail = SystemRootNodeTypeService.SYSTEM_OWNER_EMAIL,
            name = "Directory"
        )
        assertNotNull(systemDirectory, "Liquibase seed should create system Directory")

        val owner = persistUser(email = "directory-reuse-${System.nanoTime()}@test.com")
        val resolved = systemRootNodeTypeService.getOrCreate(owner, Instant.now())

        assertEquals(systemDirectory.id, resolved.id)
        assertNull(nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, "Directory"))
    }
}
