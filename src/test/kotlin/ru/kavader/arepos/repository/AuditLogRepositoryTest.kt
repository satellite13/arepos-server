package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
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

    @Test
    fun `findByChangedByAndTableName scopes results`() {
        val user = persistUser(email = "audit-scope@test.com")
        val other = persistUser(email = "audit-other@test.com")
        persistAuditLog(tableName = "models", operation = "INSERT", changedBy = user)
        persistAuditLog(tableName = "models", operation = "UPDATE", changedBy = other)
        persistAuditLog(tableName = "nodes", operation = "INSERT", changedBy = user)

        val page = auditLogRepository.findByChangedByAndTableName(
            user,
            "models",
            PageRequest.of(0, 10)
        )

        assertEquals(1, page.totalElements)
        assertEquals(user.id, page.content.single().changedBy?.id)
        assertEquals("models", page.content.single().tableName)
    }
}

