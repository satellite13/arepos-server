package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.support.PostgresContainerTest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ModelsRepositoryTest : PostgresContainerTest() {

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Autowired
    lateinit var modelsRepository: ModelsRepository

    @Test
    fun `persists model with owner`() {
        val owner = usersRepository.save(
            Users(
                email = "owner-${UUID.randomUUID()}@example.com",
                createdAt = Instant.now()
            )
        )

        val saved = modelsRepository.save(
            Models(
                name = "model-${UUID.randomUUID()}",
                createdAt = Instant.now(),
                version = "1.0.0",
                owner = owner
            )
        )

        val found = modelsRepository.findById(saved.id!!)
        assertTrue(found.isPresent)
        assertEquals(owner.id, found.get().owner.id)
    }
}

