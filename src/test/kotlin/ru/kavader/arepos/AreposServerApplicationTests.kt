package ru.kavader.arepos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.kavader.arepos.controller.ModelsController
import ru.kavader.arepos.controller.NotationsController
import ru.kavader.arepos.controller.UsersController
import ru.kavader.arepos.support.PostgresContainerTest
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AreposServerApplicationTests : PostgresContainerTest() {

    @Autowired
    lateinit var modelsController: ModelsController

    @Autowired
    lateinit var usersController: UsersController

    @Autowired
    lateinit var notationsController: NotationsController

    @Test
    fun contextLoads() {
        assertNotNull(modelsController)
        assertNotNull(usersController)
        assertNotNull(notationsController)
    }
}

