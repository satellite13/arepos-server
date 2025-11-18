package ru.kavader.arepos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.kavader.arepos.controller.HelloWorldController
import ru.kavader.arepos.controller.NotationController
import ru.kavader.arepos.support.PostgresContainerTest
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AreposServerApplicationTests : PostgresContainerTest() {

    @Autowired
    lateinit var helloWorldController: HelloWorldController

    @Autowired
    lateinit var notationController: NotationController

    @Test
    fun contextLoads() {
        assertNotNull(helloWorldController)
        assertNotNull(notationController)
    }
}

