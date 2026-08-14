package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.LibraryIcons
import java.util.UUID

@Repository
interface LibraryIconsRepository : JpaRepository<LibraryIcons, UUID> {
    @Query(
        """
        SELECT li FROM LibraryIcons li
        WHERE LOWER(li.name) = LOWER(:name)
        """
    )
    fun findByNameIgnoreCase(name: String): LibraryIcons?

    @Query(
        """
        SELECT CASE WHEN COUNT(li) > 0 THEN true ELSE false END
        FROM LibraryIcons li
        WHERE LOWER(li.name) = LOWER(:name)
        """
    )
    fun existsByNameIgnoreCase(name: String): Boolean

    fun findAllByNameIn(names: Collection<String>): List<LibraryIcons>
}
