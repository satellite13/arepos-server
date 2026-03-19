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

    @Query(
        value = """
            SELECT r.*
            FROM relations r
            WHERE (:notationId IS NULL OR r.notation = :notationId)
              AND (:ownerId IS NULL OR r.owner = :ownerId)
              AND (:name IS NULL OR r.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(r.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
              AND (
                EXISTS (
                    SELECT 1
                    FROM notations n
                    WHERE n.id = r.notation
                      AND n.deleted = false
                      AND (
                        n.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'NOTATION'
                              AND rg.resource_id = n.id
                              AND rg.permission IN ('VIEW', 'EDIT')
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
                OR EXISTS (
                    SELECT 1
                    FROM diagrams d
                    JOIN models m ON m.id = d.model
                    WHERE d.deleted = false
                      AND m.deleted = false
                      AND d.notation_id = r.notation
                      AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'MODEL'
                              AND rg.resource_id = m.id
                              AND rg.permission = 'EDIT'
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
              )
            ORDER BY r.name, r.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM relations r
            WHERE (:notationId IS NULL OR r.notation = :notationId)
              AND (:ownerId IS NULL OR r.owner = :ownerId)
              AND (:name IS NULL OR r.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(r.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
              AND (
                EXISTS (
                    SELECT 1
                    FROM notations n
                    WHERE n.id = r.notation
                      AND n.deleted = false
                      AND (
                        n.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'NOTATION'
                              AND rg.resource_id = n.id
                              AND rg.permission IN ('VIEW', 'EDIT')
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
                OR EXISTS (
                    SELECT 1
                    FROM diagrams d
                    JOIN models m ON m.id = d.model
                    WHERE d.deleted = false
                      AND m.deleted = false
                      AND d.notation_id = r.notation
                      AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'MODEL'
                              AND rg.resource_id = m.id
                              AND rg.permission = 'EDIT'
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
              )
        """,
        nativeQuery = true
    )
    fun findAccessibleByFiltersForUser(
        @Param("notationId") notationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("name") name: String?,
        @Param("tagsJson") tagsJson: String?,
        @Param("currentUserId") currentUserId: UUID,
        pageable: Pageable
    ): Page<Relations>

    @Query(
        value = """
            SELECT DISTINCT r.link_type
            FROM relations r
            WHERE r.notation = :notationId
        """,
        nativeQuery = true
    )
    fun findDistinctLinkTypeIdsByNotationId(@Param("notationId") notationId: UUID): List<UUID>

    fun existsByLinkType_IdAndNotation_IdIn(linkTypeId: UUID, notationIds: Collection<UUID>): Boolean
}


