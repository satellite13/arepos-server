package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Users
import java.util.UUID

@Repository
interface RelationsRepository : JpaRepository<Relations, UUID> {
    fun findByNotation(notation: Notations, pageable: Pageable): Page<Relations>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Relations>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Relations>
    fun findByNotationAndNameContainingIgnoreCase(notation: Notations, name: String, pageable: Pageable): Page<Relations>
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<Relations>

    @Query(
        value = """
            SELECT *
            FROM relations r
            WHERE (:notationId IS NULL OR r.notation = :notationId)
              AND (:ownerId IS NULL OR r.owner = :ownerId)
              AND (:name IS NULL OR r.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(r.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
            ORDER BY r.name, r.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM relations r
            WHERE (:notationId IS NULL OR r.notation = :notationId)
              AND (:ownerId IS NULL OR r.owner = :ownerId)
              AND (:name IS NULL OR r.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(r.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
        """,
        nativeQuery = true
    )
    fun findByFilters(
        @Param("notationId") notationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("name") name: String?,
        @Param("tagsJson") tagsJson: String?,
        pageable: Pageable
    ): Page<Relations>
}


