package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.*
import kotlin.test.assertEquals

class AdminListSupportTest {

    @Test
    fun `mapWithPermissions batch-loads permissions and preserves page metadata`() {
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val pageable = PageRequest.of(2, 5)
        val page = PageImpl(listOf(TestEntity(firstId), TestEntity(secondId)), pageable, 12)
        var loadedEntities: List<TestEntity>? = null

        val mapped = page.mapWithPermissions(
            loadPermissions = { entities ->
                loadedEntities = entities
                mapOf(firstId to "edit")
            },
            idOf = TestEntity::id
        ) { entity, permission ->
            "${entity.id}:$permission"
        }

        assertEquals(page.content, loadedEntities)
        assertEquals(listOf("$firstId:edit", "$secondId:null"), mapped.content)
        assertEquals(pageable, mapped.pageable)
        assertEquals(12, mapped.totalElements)
    }

    private data class TestEntity(val id: UUID?)
}
