package ru.kavader.arepos.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import ru.kavader.arepos.support.PostgresContainerTest
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompressionConfigTest : PostgresContainerTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    lateinit var serverProperties: ServerProperties

    @Test
    fun `returns gzip encoding for large json response`() {
        assertTrue(serverProperties.compression.enabled)

        val headersFile = Files.createTempFile("compression-test", ".headers")
        try {
            val process = ProcessBuilder(
                "curl",
                "-sS",
                "-D",
                headersFile.toString(),
                "-o",
                "/dev/null",
                "-H",
                "Accept-Encoding: gzip",
                "http://127.0.0.1:$port/v3/api-docs"
            ).start()
            val errors = process.errorStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), "curl failed: $errors")

            val headers = headersFile.readText()
            assertTrue(headers.contains("content-encoding: gzip", ignoreCase = true), headers)
        } finally {
            Files.deleteIfExists(headersFile)
        }
    }
}
