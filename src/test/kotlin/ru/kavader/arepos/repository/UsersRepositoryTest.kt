package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsersRepositoryTest : RepositoryTestBase() {

    @Test
    fun `saves and loads user by id`() {
        val saved = persistUser()
        val found = usersRepository.findById(saved.id!!)
        assertTrue(found.isPresent)
        assertEquals(saved.email, found.get().email)
    }
}

