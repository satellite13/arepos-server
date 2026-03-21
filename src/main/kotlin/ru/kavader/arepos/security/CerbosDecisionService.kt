package ru.kavader.arepos.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.kavader.arepos.config.CerbosProperties
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

data class CerbosAccessRequest(
    val resourceKind: CerbosResourceKind,
    val action: CerbosAction,
    val resourceId: UUID,
    val ownerId: UUID? = null
)

@Service
class CerbosDecisionService(
    private val cerbosProperties: CerbosProperties,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(CerbosDecisionService::class.java)
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(cerbosProperties.requestTimeout)
        .build()

    /**
     * Returns:
     *  - true/false when Cerbos returns a decision for the requested action;
     *  - null when Cerbos is disabled or response does not contain a usable decision.
     */
    fun check(request: CerbosAccessRequest): Boolean? {
        if (!cerbosProperties.enabled) {
            return null
        }

        val userId = CurrentUser.getId() ?: return null
        val role = CurrentUser.getRole() ?: "USER"

        val requestBody = mapOf(
            "requestId" to UUID.randomUUID().toString(),
            "principal" to mapOf(
                "id" to userId.toString(),
                "roles" to listOf(role),
                "attr" to mapOf("role" to role)
            ),
            "resources" to listOf(
                mapOf(
                    "resource" to mapOf(
                        "kind" to request.resourceKind.policyValue,
                        "id" to request.resourceId.toString(),
                        "attr" to mapOf(
                            "ownerId" to request.ownerId?.toString(),
                            "resourceKind" to request.resourceKind.policyValue
                        )
                    ),
                    "actions" to listOf(request.action.policyValue)
                )
            )
        )

        val payload = objectMapper.writeValueAsString(requestBody)
        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("${cerbosProperties.endpoint.trimEnd('/')}/api/check/resources"))
                .timeout(cerbosProperties.requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Cerbos request failed with status ${response.statusCode()}")
        }

        return parseDecision(response.body(), request.action.policyValue)
    }

    private fun parseDecision(responseBody: String, action: String): Boolean? {
        val root = objectMapper.readTree(responseBody)
        val results = root.path("results")
        if (!results.isArray || results.isEmpty) {
            log.debug("Cerbos response has no results")
            return null
        }

        val effect = results.first().path("actions").path(action).asText(null) ?: return null
        return when (effect) {
            "EFFECT_ALLOW", "ALLOW" -> true
            "EFFECT_DENY", "DENY" -> false
            else -> {
                log.debug("Unknown Cerbos effect received: {}", effect)
                null
            }
        }
    }
}
