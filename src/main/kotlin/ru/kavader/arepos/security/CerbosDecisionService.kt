package ru.kavader.arepos.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.kavader.arepos.config.CerbosProperties
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID

data class CerbosAccessRequest(
    val resourceKind: CerbosResourceKind,
    val action: CerbosAction,
    val resourceId: UUID,
    val ownerId: UUID? = null,
    val resourceAttributes: Map<String, Any?> = emptyMap()
)

data class CerbosBatchAccessRequest(
    val resourceKind: CerbosResourceKind,
    val action: CerbosAction,
    val resourceId: UUID,
    val ownerId: UUID? = null,
    val resourceAttributes: Map<String, Any?> = emptyMap()
)

@Service
class CerbosDecisionService(
    private val cerbosProperties: CerbosProperties,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(CerbosDecisionService::class.java)
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(cerbosProperties.requestTimeout)
        .readTimeout(cerbosProperties.requestTimeout)
        .callTimeout(cerbosProperties.requestTimeout)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .build()
    private val cerbosCheckMediaType = "application/json".toMediaType()
    private val circuitBreaker: CircuitBreaker = CircuitBreaker.of(
        "cerbos-authz",
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(cerbosProperties.circuitFailureThreshold.coerceAtLeast(2))
            .minimumNumberOfCalls(cerbosProperties.circuitFailureThreshold.coerceAtLeast(2))
            .waitDurationInOpenState(cerbosProperties.circuitOpenDuration)
            .permittedNumberOfCallsInHalfOpenState(1)
            .recordExceptions(Exception::class.java)
            .build()
    )

    fun check(request: CerbosAccessRequest): Boolean {
        return checkBatch(
            listOf(
                CerbosBatchAccessRequest(
                    resourceKind = request.resourceKind,
                    action = request.action,
                    resourceId = request.resourceId,
                    ownerId = request.ownerId,
                    resourceAttributes = request.resourceAttributes
                )
            )
        )[request.resourceId] ?: throw IllegalStateException(
            "Cerbos decision missing for resourceId=${request.resourceId}, action=${request.action.policyValue}"
        )
    }

    fun checkBatch(requests: List<CerbosBatchAccessRequest>): Map<UUID, Boolean> {
        if (requests.isEmpty()) {
            return emptyMap()
        }

        val userId = CurrentUser.getId() ?: throw IllegalStateException("Cerbos check requires authenticated principal")
        val role = CurrentUser.getRole() ?: "USER"

        val requestBody = mapOf(
            "requestId" to UUID.randomUUID().toString(),
            "principal" to mapOf(
                "id" to userId.toString(),
                "roles" to listOf(role),
                "attr" to mapOf("role" to role)
            ),
            "resources" to requests.map { request ->
                mapOf(
                    "resource" to mapOf(
                        "kind" to request.resourceKind.policyValue,
                        "id" to request.resourceId.toString(),
                        "attr" to mapOf(
                            "ownerId" to request.ownerId?.toString(),
                            "resourceKind" to request.resourceKind.policyValue
                        )
                            .plus(request.resourceAttributes)
                    ),
                    "actions" to listOf(request.action.policyValue)
                )
            }
        )

        val payload = objectMapper.writeValueAsString(requestBody)
        val responseBody = try {
            val executeWithCircuit = CircuitBreaker.decorateSupplier(circuitBreaker) {
                executeCerbosRequest(payload)
            }
            executeWithCircuit.get()
        } catch (ex: Exception) {
            log.warn(
                "Cerbos check failed, applying default-deny fallback: state={}, reason={}, requests={}",
                circuitBreaker.state,
                ex::class.simpleName ?: "UnknownException",
                requests.size,
                ex
            )
            return defaultDeny(requests)
        }

        return try {
            parseBatchDecisions(responseBody, requests)
        } catch (ex: Exception) {
            log.warn(
                "Cerbos response parse failed, applying default-deny fallback: state={}, requests={}",
                circuitBreaker.state,
                requests.size,
                ex
            )
            defaultDeny(requests)
        }
    }

    private fun executeCerbosRequest(payload: String): String {
        val request = Request.Builder()
            .url("${cerbosProperties.endpoint.trimEnd('/')}/api/check/resources")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(cerbosCheckMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Cerbos request failed with status ${response.code}")
            }
            return response.body?.string()
                ?: throw IOException("Cerbos response body is empty")
        }
    }

    private fun parseBatchDecisions(
        responseBody: String,
        requests: List<CerbosBatchAccessRequest>
    ): Map<UUID, Boolean> {
        val root = objectMapper.readTree(responseBody)
        val results = root.path("results")
        if (!results.isArray || results.isEmpty) {
            throw IllegalStateException("Cerbos response has no results")
        }

        val requestById = requests.associateBy { it.resourceId.toString() }
        val decisions = mutableMapOf<UUID, Boolean>()

        results.forEachIndexed { index, resultNode ->
            val responseResourceId = resultNode.path("resource").path("id").asText(null)
            val request = if (responseResourceId != null) {
                requestById[responseResourceId]
            } else {
                requests.getOrNull(index)
            } ?: return@forEachIndexed

            val effect = resultNode.path("actions").path(request.action.policyValue).asText(null)
            decisions[request.resourceId] = parseEffect(effect)
        }

        return requests.associate { request ->
            request.resourceId to (decisions[request.resourceId]
                ?: throw IllegalStateException(
                    "Cerbos response missing decision for resourceId=${request.resourceId}, action=${request.action.policyValue}"
                ))
        }
    }

    private fun parseEffect(effect: String?): Boolean {
        if (effect == null) {
            throw IllegalStateException("Cerbos response missing action effect")
        }
        return when (effect) {
            "EFFECT_ALLOW", "ALLOW" -> true
            "EFFECT_DENY", "DENY" -> false
            else -> throw IllegalStateException("Unknown Cerbos effect received: $effect")
        }
    }

    private fun defaultDeny(requests: List<CerbosBatchAccessRequest>): Map<UUID, Boolean> =
        requests.associate { it.resourceId to false }
}
