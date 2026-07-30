package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.kavader.arepos.model.NodeTypes
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.RepositoryTestBase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class SystemRootNodeTypeServiceTest : RepositoryTestBase() {

    @Autowired
    lateinit var systemRootNodeTypeService: SystemRootNodeTypeService

    @Test
    fun `getOrCreate reuses system Directory instead of creating per owner`() {
        val systemDirectory = ensureSystemDirectory()
        val owner = persistUser(email = "directory-reuse-${System.nanoTime()}@test.com")
        val resolved = systemRootNodeTypeService.getOrCreate(owner, Instant.now())

        assertEquals(systemDirectory.id, resolved.id)
        assertNull(nodeTypesRepository.findByOwnerAndNameIgnoreCase(owner, "Directory"))
    }

    @Test
    fun `system Directory is protected from mutation`() {
        val systemDirectory = ensureSystemDirectory()
        assertTrue(systemRootNodeTypeService.isProtectedSystemDirectory(systemDirectory))

        val regular = persistNodeType(owner = persistUser(), name = "Business Actor")
        assertFalse(systemRootNodeTypeService.isProtectedSystemDirectory(regular))
    }

    private fun ensureSystemDirectory(): NodeTypes {
        val existing = nodeTypesRepository.findByOwnerEmailIgnoreCaseAndNameIgnoreCase(
            ownerEmail = SystemRootNodeTypeService.SYSTEM_OWNER_EMAIL,
            name = "Directory"
        )
        if (existing != null) return existing

        val systemUser = usersRepository.findByEmailIgnoreCase(SystemRootNodeTypeService.SYSTEM_OWNER_EMAIL)
            ?: usersRepository.save(
                Users(
                    email = SystemRootNodeTypeService.SYSTEM_OWNER_EMAIL,
                    role = Role.USER,
                    isActive = false,
                    createdAt = Instant.now(),
                    attrs = """{"system":true}"""
                )
            )
        return nodeTypesRepository.save(
            NodeTypes(
                name = "Directory",
                attrs = """{"system":{"hiddenTreeRootType":true}}""",
                createdAt = Instant.now(),
                owner = systemUser
            )
        )
    }
}
