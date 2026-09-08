package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.kavader.arepos.model.UserDiagramFavorite
import java.util.UUID

interface UserDiagramFavoriteRepository : JpaRepository<UserDiagramFavorite, UUID> {
    fun findByDiagramIdAndUserId(diagramId: UUID, userId: UUID): UserDiagramFavorite?
    fun existsByDiagramIdAndUserId(diagramId: UUID, userId: UUID): Boolean

    fun deleteByDiagramIdAndUserId(diagramId: UUID, userId: UUID): Long

    fun countByDiagramId(diagramId: UUID): Int

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<UserDiagramFavorite>

    @Query(
        "SELECT f.diagram.id FROM UserDiagramFavorite f WHERE f.user.id = :userId AND f.diagram.deleted = false"
    )
    fun findActiveDiagramIdsByUserId(userId: UUID): List<UUID>

    @Query(
        """
        SELECT f FROM UserDiagramFavorite f
        JOIN FETCH f.diagram d
        JOIN FETCH d.model
        WHERE f.user.id = :userId AND d.deleted = false
        ORDER BY f.createdAt DESC
        """
    )
    fun findActiveFavoritesWithDiagramAndModelByUserId(userId: UUID): List<UserDiagramFavorite>
}
