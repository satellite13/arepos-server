package ru.kavader.arepos.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import ru.kavader.arepos.config.CerbosMode
import ru.kavader.arepos.config.CerbosProperties
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CerbosDecisionServiceTest {
    @AfterEach
    fun cleanupAuth() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `returns null when cerbos disabled`() {
        val service = CerbosDecisionService(
            cerbosProperties = CerbosProperties(enabled = false),
            objectMapper = jacksonObjectMapper()
        )

        setCurrentUser(role = "USER")
        val decision = service.check(
            CerbosAccessRequest(
                resourceKind = CerbosResourceKind.MODEL,
                action = CerbosAction.VIEW,
                resourceId = UUID.randomUUID()
            )
        )

        assertNull(decision)
    }

    @Test
    fun `parses allow decision from cerbos response`() {
        withServer("""{"results":[{"actions":{"view":"EFFECT_ALLOW"}}]}""") { endpoint ->
            val service = CerbosDecisionService(
                cerbosProperties = CerbosProperties(
                    enabled = true,
                    mode = CerbosMode.SHADOW,
                    endpoint = endpoint,
                    requestTimeout = Duration.ofSeconds(1)
                ),
                objectMapper = jacksonObjectMapper()
            )

            setCurrentUser(role = "ADMIN")
            val decision = service.check(
                CerbosAccessRequest(
                    resourceKind = CerbosResourceKind.MODEL,
                    action = CerbosAction.VIEW,
                    resourceId = UUID.randomUUID()
                )
            )

            assertEquals(true, decision)
        }
    }

    @Test
    fun `parses deny decision from cerbos response`() {
        withServer("""{"results":[{"actions":{"edit":"EFFECT_DENY"}}]}""") { endpoint ->
            val service = CerbosDecisionService(
                cerbosProperties = CerbosProperties(
                    enabled = true,
                    mode = CerbosMode.ENFORCE,
                    endpoint = endpoint,
                    requestTimeout = Duration.ofSeconds(1)
                ),
                objectMapper = jacksonObjectMapper()
            )

            setCurrentUser(role = "USER")
            val decision = service.check(
                CerbosAccessRequest(
                    resourceKind = CerbosResourceKind.NOTATION,
                    action = CerbosAction.EDIT,
                    resourceId = UUID.randomUUID(),
                    ownerId = UUID.randomUUID()
                )
            )

            assertEquals(false, decision)
        }
    }

    private fun setCurrentUser(role: String) {
        val auth = UsernamePasswordAuthenticationToken(
            UUID.randomUUID(),
            null,
            listOf(SimpleGrantedAuthority("ROLE_$role"))
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun withServer(body: String, block: (endpoint: String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/check/resources") { exchange ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block("http://localhost:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
