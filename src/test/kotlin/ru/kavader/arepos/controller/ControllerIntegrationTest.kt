package ru.kavader.arepos.controller

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.security.JwtTokenProvider
import ru.kavader.arepos.support.PostgresContainerTest
import java.util.*

abstract class ControllerIntegrationTest : PostgresContainerTest() {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var jwtTokenProvider: JwtTokenProvider

    @BeforeEach
    fun truncateTables() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                public.audit_log,
                public.model_sync_outbox,
                public.resource_shares,
                public.diagram_preview_links,
                public.document_refs,
                public.file_versions,
                public.diagram_edit_locks,
                public.refresh_tokens,
                public.files,
                public.relation_rules,
                public.relations,
                public.links,
                public.components,
                public.diagrams,
                public.nodes,
                public.node_shapes,
                public.node_types,
                public.link_types,
                public.notations,
                public.models,
                public.users
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    fun bearerToken(userId: UUID, role: Role = Role.ADMIN): String {
        return "Bearer ${jwtTokenProvider.generateAccessToken(userId, role.name)}"
    }

    fun MockHttpServletRequestBuilder.withAuth(userId: UUID, role: Role = Role.ADMIN): MockHttpServletRequestBuilder {
        return this.header("Authorization", bearerToken(userId, role))
    }
}
