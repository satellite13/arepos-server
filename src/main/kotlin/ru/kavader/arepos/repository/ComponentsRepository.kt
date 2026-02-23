package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Components
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Users
import java.util.UUID

@Repository
interface ComponentsRepository : JpaRepository<Components, UUID> {
    fun findByNotation(notation: Notations, pageable: Pageable): Page<Components>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Components>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Components>
    fun findByNotationAndNameContainingIgnoreCase(notation: Notations, name: String, pageable: Pageable): Page<Components>
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<Components>

    @Query(
        value = """
            SELECT *
            FROM components c
            WHERE (:notationId IS NULL OR c.notation = :notationId)
              AND (:ownerId IS NULL OR c.owner = :ownerId)
              AND (:name IS NULL OR c.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(c.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
            ORDER BY c.name, c.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM components c
            WHERE (:notationId IS NULL OR c.notation = :notationId)
              AND (:ownerId IS NULL OR c.owner = :ownerId)
              AND (:name IS NULL OR c.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(c.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
        """,
        nativeQuery = true
    )
    fun findByFilters(
        @Param("notationId") notationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("name") name: String?,
        @Param("tagsJson") tagsJson: String?,
        pageable: Pageable
    ): Page<Components>
}


