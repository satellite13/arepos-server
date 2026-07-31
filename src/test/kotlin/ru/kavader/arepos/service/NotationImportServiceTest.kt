package ru.kavader.arepos.service

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.kavader.arepos.dto.import.ImportedComponent
import ru.kavader.arepos.dto.import.ImportedLinkType
import ru.kavader.arepos.dto.import.ImportedNodeType
import ru.kavader.arepos.dto.import.ImportedRelation
import ru.kavader.arepos.dto.import.ImportedRelationRule
import ru.kavader.arepos.dto.import.NotationImportMeta
import ru.kavader.arepos.dto.import.NotationImportRequest
import ru.kavader.arepos.repository.RepositoryTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class NotationImportServiceTest : RepositoryTestBase() {

    @Autowired
    lateinit var notationImportService: NotationImportService

    @Test
    fun `import bulk-inserts relation rules and ignores in-payload duplicates`() {
        val owner = persistUser(email = "notation-bulk-rules@test.com")
        val componentCount = 40
        val components = (1..componentCount).map { i ->
            ImportedComponent(
                id = "c$i",
                name = "Component $i",
                nodeTypeId = "nt1"
            )
        }
        // Dense allow-list: every ordered pair gets the same relation → N*(N-1) unique rules.
        val rules = components.flatMap { from ->
            components
                .filter { it.id != from.id }
                .map { to ->
                    ImportedRelationRule(
                        fromComponentId = from.id,
                        toComponentId = to.id,
                        allowedRelationIds = listOf("rel1", "rel1") // duplicate in payload
                    )
                }
        }
        val expectedRules = componentCount * (componentCount - 1)

        val response = notationImportService.import(
            NotationImportRequest(
                notation = NotationImportMeta(name = "Bulk rules notation", version = "1.0.0"),
                nodeTypes = listOf(ImportedNodeType(id = "nt1", name = "Bulk Node")),
                linkTypes = listOf(ImportedLinkType(id = "lt1", name = "Bulk Link")),
                components = components,
                relations = listOf(
                    ImportedRelation(id = "rel1", name = "Association", linkTypeId = "lt1")
                ),
                relationRules = rules
            ),
            owner
        )

        assertEquals(expectedRules.toLong(), relationRulesRepository.count())
        assertTrue(response.componentIdMap.size == componentCount)
        assertEquals(1, response.relationIdMap.size)
    }
}
