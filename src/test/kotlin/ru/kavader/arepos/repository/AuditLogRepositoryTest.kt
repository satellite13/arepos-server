package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditLogRepositoryTest : RepositoryTestBase() {

    @Test
    fun `stores audit record for entity`() {
        val log = persistAuditLog()

        val found = auditLogRepository.findById(log.id!!)
        assertTrue(found.isPresent)
        assertEquals("users", found.get().tableName)
    }
}

