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
class LargeModelLinkReadIndexesMigrationTest : PostgresContainerTest() {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `migration rebuilds pre-existing named indexes and leaves them valid`() {
        dropIndexes()
        jdbcTemplate.execute("CREATE INDEX links_model_source_idx ON public.links (model, id)")
        jdbcTemplate.execute("CREATE INDEX links_model_target_idx ON public.links (model, id)")

        try {
            migrationStatements().forEach(jdbcTemplate::execute)

            assertIndex("links_model_source_idx", "(model, source)")
            assertIndex("links_model_target_idx", "(model, target)")
        } finally {
            dropIndexes()
            jdbcTemplate.execute(
                "CREATE INDEX CONCURRENTLY links_model_source_idx ON public.links (model, source)"
            )
            jdbcTemplate.execute(
                "CREATE INDEX CONCURRENTLY links_model_target_idx ON public.links (model, target)"
            )
        }
    }

    private fun migrationStatements(): List<String> =
        ClassPathResource("db/changelog/055-large-model-link-read-indexes.sql")
            .inputStream
            .bufferedReader()
            .use { it.readText() }
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)

    private fun assertIndex(indexName: String, expectedColumns: String) {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT pg_get_indexdef(indexrelid) AS definition, indisvalid
            FROM pg_index
            WHERE indexrelid = ?::regclass
            """.trimIndent(),
            "public.$indexName"
        )
        assertTrue(row["indisvalid"] as Boolean)
        assertContains(row["definition"].toString(), expectedColumns)
    }

    private fun dropIndexes() {
        jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS public.links_model_source_idx")
        jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS public.links_model_target_idx")
    }
}
