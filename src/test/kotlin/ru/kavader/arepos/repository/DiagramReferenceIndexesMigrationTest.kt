package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import ru.kavader.arepos.support.PostgresContainerTest
import kotlin.test.assertContains
import kotlin.test.assertTrue

@SpringBootTest
class DiagramReferenceIndexesMigrationTest : PostgresContainerTest() {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `concurrent migration is retry safe and rebuilds invalid definitions`() {
        dropIndexes()
        jdbcTemplate.execute("CREATE INDEX diagrams_active_attrs_path_idx ON public.diagrams (model)")
        jdbcTemplate.execute("CREATE INDEX diagrams_active_model_name_id_idx ON public.diagrams (id)")

        try {
            migrationStatements().forEach(jdbcTemplate::execute)
            migrationStatements().forEach(jdbcTemplate::execute)

            assertIndex(
                "diagrams_active_attrs_path_idx",
                "USING gin (attrs jsonb_path_ops) WHERE (deleted = false)"
            )
            assertIndex(
                "diagrams_active_model_name_id_idx",
                "(model, name, id) WHERE (deleted = false)"
            )
        } finally {
            dropIndexes()
            migrationStatements().forEach(jdbcTemplate::execute)
        }
    }

    @Test
    fun `changelog runs concurrent migration outside a transaction`() {
        val changelog = ClassPathResource("db/changelog/db.changelog-master.yaml")
            .inputStream
            .bufferedReader()
            .use { it.readText() }
        val changeSet = changelog.substringAfter("id: 056-diagram-reference-indexes")
            .substringBefore("\n  - changeSet:")

        assertContains(changeSet, "runInTransaction: false")
        assertContains(changeSet, "splitStatements: true")
    }

    private fun migrationStatements(): List<String> =
        ClassPathResource("db/changelog/056-diagram-reference-indexes.sql")
            .inputStream
            .bufferedReader()
            .use { it.readText() }
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)

    private fun assertIndex(indexName: String, expectedDefinition: String) {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT pg_get_indexdef(indexrelid) AS definition, indisvalid
            FROM pg_index
            WHERE indexrelid = ?::regclass
            """.trimIndent(),
            "public.$indexName"
        )
        assertTrue(row["indisvalid"] as Boolean)
        assertContains(row["definition"].toString(), expectedDefinition)
    }

    private fun dropIndexes() {
        jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS public.diagrams_active_attrs_path_idx")
        jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS public.diagrams_active_model_name_id_idx")
    }
}
