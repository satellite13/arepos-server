package ru.kavader.arepos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import ru.kavader.arepos.controller.HelloWorldController
import ru.kavader.arepos.controller.NotationController
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AreposServerApplicationTests {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate

    @Autowired
    lateinit var helloWorldController: HelloWorldController

    @Autowired
    lateinit var notationController: NotationController

    @Test
    fun contextLoads() {
        assertNotNull(helloWorldController, "helloWorldController == null")
        assertNotNull(notationController, "notationController == null")
    }

    @Test
    fun helloWorldWebTest() {
        assertContains(
            testRestTemplate.getForObject<String>(
                "http://localhost:$port/hello-world",
                String::class.java
            ),
            "Hello World"
        )
    }

    @Test
    fun notationWebTest() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val httpEntity = HttpEntity<String>("{\"name\":\"Hello World\"}", headers)
        assertEquals(
            testRestTemplate.postForEntity<String>(
                "http://localhost:$port/api/v1/notation/12345",
                httpEntity,
                String::class.java,
            ).statusCode.value(), 200
        )
    }
}
