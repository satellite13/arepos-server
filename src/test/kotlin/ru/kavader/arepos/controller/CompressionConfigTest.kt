package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestTemplate
import ru.kavader.arepos.support.PostgresContainerTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompressionConfigTest : PostgresContainerTest() {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `returns gzip encoding for large json response`() {
        val requestFactory = SimpleClientHttpRequestFactory()
        val rawTemplate = RestTemplate(requestFactory).apply {
            messageConverters.add(0, StringHttpMessageConverter())
        }
        val headers = HttpHeaders().apply {
            set(HttpHeaders.ACCEPT_ENCODING, "gzip")
            accept = listOf(MediaType.APPLICATION_JSON)
        }

        val response = rawTemplate.exchange(
            "http://localhost:$port/v3/api-docs",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            ByteArray::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val encodings = response.headers[HttpHeaders.CONTENT_ENCODING].orEmpty()
        assertTrue(encodings.any { it.contains("gzip") })
    }
}
