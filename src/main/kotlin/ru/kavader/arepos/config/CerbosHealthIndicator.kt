package ru.kavader.arepos.config

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component("cerbos")
class CerbosHealthIndicator(
    private val cerbosProperties: CerbosProperties
) : HealthIndicator {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(cerbosProperties.requestTimeout)
        .build()

    override fun health(): Health {
        val endpoint = cerbosProperties.endpoint.trimEnd('/')
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$endpoint/_cerbos/health"))
            .timeout(cerbosProperties.requestTimeout)
            .GET()
            .build()
        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() in 200..299) {
                Health.up()
                    .withDetail("endpoint", endpoint)
                    .withDetail("status", response.statusCode())
                    .build()
            } else {
                Health.down()
                    .withDetail("endpoint", endpoint)
                    .withDetail("status", response.statusCode())
                    .build()
            }
        } catch (ex: Exception) {
            Health.down(ex)
                .withDetail("endpoint", endpoint)
                .build()
        }
    }
}
