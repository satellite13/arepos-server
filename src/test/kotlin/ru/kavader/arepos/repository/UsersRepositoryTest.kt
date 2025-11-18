package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.support.PostgresContainerTest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsersRepositoryTest : PostgresContainerTest() {

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Test
    fun `saves and loads user by id`() {
        val saved = usersRepository.save(
            Users(
                email = "user-${UUID.randomUUID()}@example.com",
                attrs = """{"role":"tester"}""",
                createdAt = Instant.now()
            )
        )

        val found = usersRepository.findById(saved.id!!)
        assertTrue(found.isPresent)
        assertEquals(saved.email, found.get().email)
    }
}

