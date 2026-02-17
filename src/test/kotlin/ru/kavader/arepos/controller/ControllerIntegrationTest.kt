package ru.kavader.arepos.controller

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import ru.kavader.arepos.support.PostgresContainerTest

abstract class ControllerIntegrationTest : PostgresContainerTest() {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun truncateTables() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE 
                public.audit_log,
                public.relation_rules,
                public.relations,
                public.links,
                public.components,
                public.diagrams,
                public.nodes,
                public.node_types,
                public.link_types,
                public.notations,
                public.models,
                public.users
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }
}


