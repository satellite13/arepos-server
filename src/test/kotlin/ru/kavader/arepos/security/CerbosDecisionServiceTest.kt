package ru.kavader.arepos.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import ru.kavader.arepos.config.CerbosProperties
import java.net.InetSocketAddress
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CerbosDecisionServiceTest {
    @AfterEach
    fun cleanupAuth() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `fails when no authenticated principal`() {
        val service = CerbosDecisionService(
            cerbosProperties = CerbosProperties(),
            objectMapper = jacksonObjectMapper()
        )

        assertFailsWith<IllegalStateException> {
            service.check(
                CerbosAccessRequest(
                    resourceKind = CerbosResourceKind.MODEL,
                    action = CerbosAction.VIEW,
                    resourceId = UUID.randomUUID()
                )
            )
        }
    }

    @Test
    fun `parses allow decision from cerbos response`() {
        withServer("""{"results":[{"actions":{"view":"EFFECT_ALLOW"}}]}""") { endpoint ->
            val service = CerbosDecisionService(
                cerbosProperties = CerbosProperties(
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

    @Test
    fun `includes extra resource attributes in request payload`() {
        val capturedBody = AtomicReference("")
        withServer(
            body = """{"results":[{"actions":{"view":"EFFECT_ALLOW"}}]}""",
            onRequest = { payload -> capturedBody.set(payload) }
        ) { endpoint ->
            val service = CerbosDecisionService(
                cerbosProperties = CerbosProperties(
                    endpoint = endpoint,
                    requestTimeout = Duration.ofSeconds(1)
                ),
                objectMapper = jacksonObjectMapper()
            )

            setCurrentUser(role = "USER")
            service.check(
                CerbosAccessRequest(
                    resourceKind = CerbosResourceKind.MODEL,
                    action = CerbosAction.VIEW,
                    resourceId = UUID.randomUUID(),
                    ownerId = UUID.randomUUID(),
                    resourceAttributes = mapOf(
                        "isOwner" to false,
                        "hasShareView" to true,
                        "hasShareEdit" to false
                    )
                )
            )
        }

        val payload = capturedBody.get()
        assertTrue(payload.contains("\"hasShareView\":true"))
        assertTrue(payload.contains("\"hasShareEdit\":false"))
        assertTrue(payload.contains("\"isOwner\":false"))
    }

    @Test
    fun `supports batch decisions in single request`() {
        val capturedBody = AtomicReference("")
        withServer(
            body = """{"results":[{"resource":{"id":"11111111-1111-1111-1111-111111111111"},"actions":{"view":"EFFECT_ALLOW"}},{"resource":{"id":"22222222-2222-2222-2222-222222222222"},"actions":{"view":"EFFECT_DENY"}}]}""",
            onRequest = { payload -> capturedBody.set(payload) }
        ) { endpoint ->
            val service = CerbosDecisionService(
                cerbosProperties = CerbosProperties(
                    endpoint = endpoint,
                    requestTimeout = Duration.ofSeconds(1)
                ),
                objectMapper = jacksonObjectMapper()
            )

            setCurrentUser(role = "USER")
            val allowId = UUID.fromString("11111111-1111-1111-1111-111111111111")
            val denyId = UUID.fromString("22222222-2222-2222-2222-222222222222")
            val decisions = service.checkBatch(
                listOf(
                    CerbosBatchAccessRequest(
                        resourceKind = CerbosResourceKind.MODEL,
                        action = CerbosAction.VIEW,
                        resourceId = allowId
                    ),
                    CerbosBatchAccessRequest(
                        resourceKind = CerbosResourceKind.MODEL,
                        action = CerbosAction.VIEW,
                        resourceId = denyId
                    )
                )
            )

            assertEquals(true, decisions[allowId])
            assertEquals(false, decisions[denyId])
        }

        val payload = capturedBody.get()
        assertTrue(payload.contains("\"resources\":["))
        assertTrue(payload.contains("11111111-1111-1111-1111-111111111111"))
        assertTrue(payload.contains("22222222-2222-2222-2222-222222222222"))
        assertFalse(payload.contains("\"resources\":[]"))
    }

    @Test
    fun `splits large batch into multiple cerbos requests`() {
        val requestCount = AtomicInteger(0)
        val objectMapper = jacksonObjectMapper()
        withDynamicBatchServer(
            onRequest = { _ -> requestCount.incrementAndGet() },
            responseFor = { payload ->
                val resources = objectMapper.readTree(payload).path("resources")
                val results = (0 until resources.size()).joinToString(",") { index ->
                    """{"resource":{"id":${resources[index].path("resource").path("id")}},"actions":{"view":"EFFECT_ALLOW"}}"""
                }
                """{"results":[$results]}"""
            }
        ) { endpoint ->
            val service = CerbosDecisionService(
                cerbosProperties = CerbosProperties(
                    endpoint = endpoint,
                    requestTimeout = Duration.ofSeconds(1),
                    batchChunkSize = 2
                ),
                objectMapper = objectMapper
            )

            setCurrentUser(role = "USER")
            val resourceIds = List(3) { UUID.randomUUID() }
            val decisions = service.checkBatch(
                resourceIds.map { resourceId ->
                    CerbosBatchAccessRequest(
                        resourceKind = CerbosResourceKind.NODE_TYPE,
                        action = CerbosAction.VIEW,
                        resourceId = resourceId
                    )
                }
            )

            assertEquals(2, requestCount.get())
            assertEquals(3, decisions.size)
            resourceIds.forEach { resourceId ->
                assertEquals(true, decisions[resourceId])
            }
        }
    }

    @Test
    fun `opens circuit after repeated cerbos failures`() {
        withServer(
            body = """{"error":"unavailable"}""",
            statusCode = 503
        ) { endpoint ->
            val service = CerbosDecisionService(
                cerbosProperties = CerbosProperties(
                    endpoint = endpoint,
                    requestTimeout = Duration.ofSeconds(1),
                    circuitFailureThreshold = 2,
                    circuitOpenDuration = Duration.ofSeconds(30)
                ),
                objectMapper = jacksonObjectMapper()
            )
            setCurrentUser(role = "USER")
            val request = CerbosAccessRequest(
                resourceKind = CerbosResourceKind.MODEL,
                action = CerbosAction.VIEW,
                resourceId = UUID.randomUUID()
            )

            assertFalse(service.check(request))
            assertFalse(service.check(request))
            assertFalse(service.check(request))
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

    private fun withDynamicBatchServer(
        onRequest: ((payload: String) -> Unit)? = null,
        responseFor: (payload: String) -> String,
        block: (endpoint: String) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/check/resources") { exchange ->
            val payload = exchange.requestBody.bufferedReader().use { it.readText() }
            onRequest?.invoke(payload)
            val body = responseFor(payload)
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

    private fun withServer(
        body: String,
        statusCode: Int = 200,
        onRequest: ((payload: String) -> Unit)? = null,
        block: (endpoint: String) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/check/resources") { exchange ->
            val payload = exchange.requestBody.bufferedReader().use { it.readText() }
            onRequest?.invoke(payload)
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
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
