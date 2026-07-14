package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
class ControllerIntegrationTestTest : ControllerIntegrationTest() {

    @Test
    fun `truncateTables clears scheduler locks`() {
        val now = Timestamp.from(Instant.now())
        jdbcTemplate.update(
            """
            INSERT INTO public.shedlock (name, lock_until, locked_at, locked_by)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            "test-lock",
            now,
            now,
            "test",
        )

        truncateTables()

        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.shedlock", Int::class.java),
        )
    }
}
