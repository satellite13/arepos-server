package ru.kavader.arepos.repository

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import ru.kavader.arepos.model.UserDiagramFavorite
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserDiagramFavoriteRepositoryTest : RepositoryTestBase() {

    @Test
    fun `unique constraint rejects duplicate user diagram pair`() {
        val diagram = persistDiagram()
        persistFavorite(user = diagram.owner, diagram = diagram)

        assertFailsWith<Exception> {
            userDiagramFavoriteRepository.saveAndFlush(
                UserDiagramFavorite(
                    user = diagram.owner,
                    diagram = diagram,
                    createdAt = Instant.now()
                )
            )
        }
    }

    @Test
    fun `findActiveDiagramIdsByUserId returns only own favorites of active diagrams`() {
        val user = persistUser()
        val otherUser = persistUser()
        val diagram = persistDiagram()
        val deletedDiagram = persistDiagram()
        deletedDiagram.deleted = true
        diagramsRepository.save(deletedDiagram)

        persistFavorite(user = user, diagram = diagram)
        persistFavorite(user = user, diagram = deletedDiagram)
        persistFavorite(user = otherUser, diagram = persistDiagram())

        val ids = userDiagramFavoriteRepository.findActiveDiagramIdsByUserId(user.id!!)
        assertEquals(1, ids.size)
        assertEquals(diagram.id, ids.single())
    }

    @Test
    fun `findActiveDiagramsWithModelByUserId orders by createdAt desc and excludes deleted`() {
        val user = persistUser()
        val older = persistFavorite(user = user, createdAt = Instant.now().minusSeconds(60))
        val newer = persistFavorite(user = user, createdAt = Instant.now())
        val deleted = persistDiagram()
        deleted.deleted = true
        diagramsRepository.save(deleted)
        persistFavorite(user = user, diagram = deleted, createdAt = Instant.now().minusSeconds(30))
        persistFavorite(user = persistUser(), diagram = persistDiagram(), createdAt = Instant.now())

        val favorites = userDiagramFavoriteRepository
            .findActiveFavoritesWithDiagramAndModelByUserId(user.id!!)
            .map { it.diagram }
        assertEquals(2, favorites.size)
        assertEquals(newer.diagram.id, favorites[0].id)
        assertEquals(older.diagram.id, favorites[1].id)
        assertTrue(favorites.all { it.model.name.isNotEmpty() })
    }

    @Test
    fun `deleteByDiagramIdAndUserId removes only that user's row`() {
        val diagram = persistDiagram()
        val user = persistUser()
        val otherUser = persistUser()
        persistFavorite(user = user, diagram = diagram)
        persistFavorite(user = otherUser, diagram = diagram)

        val removed = userDiagramFavoriteRepository.deleteByDiagramIdAndUserId(diagram.id!!, user.id!!)

        assertEquals(1, removed)
        assertFalse(userDiagramFavoriteRepository.existsByDiagramIdAndUserId(diagram.id!!, user.id!!))
        assertTrue(userDiagramFavoriteRepository.existsByDiagramIdAndUserId(diagram.id!!, otherUser.id!!))
    }

    @Test
    fun `favorites cascade on diagram delete`() {
        val diagram = persistDiagram()
        val favorite = persistFavorite(diagram = diagram)

        diagramsRepository.hardDeleteById(diagram.id!!)
        userDiagramFavoriteRepository.flush()

        assertFalse(userDiagramFavoriteRepository.existsById(favorite.id!!))
    }
}
