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
import java.util.*

@Repository
interface ComponentsRepository : JpaRepository<Components, UUID> {
    fun findByNotation(notation: Notations, pageable: Pageable): Page<Components>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Components>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Components>

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

    @Query(
        value = """
            SELECT c.*
            FROM components c
            WHERE (:notationId IS NULL OR c.notation = :notationId)
              AND (:ownerId IS NULL OR c.owner = :ownerId)
              AND (:name IS NULL OR c.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(c.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
              AND (
                EXISTS (
                    SELECT 1
                    FROM notations n
                    WHERE n.id = c.notation
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
                      AND d.notation_id = c.notation
                      AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'MODEL'
                              AND rg.resource_id = m.id
                              AND rg.permission IN ('VIEW', 'EDIT')
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
                OR (
                  :diagramEditorModelId IS NOT NULL
                  AND :notationId IS NOT NULL
                  AND c.notation = :notationId
                  AND EXISTS (
                    SELECT 1
                    FROM models m
                    WHERE m.id = :diagramEditorModelId
                      AND m.deleted = false
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
              )
            ORDER BY c.name, c.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM components c
            WHERE (:notationId IS NULL OR c.notation = :notationId)
              AND (:ownerId IS NULL OR c.owner = :ownerId)
              AND (:name IS NULL OR c.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(c.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
              AND (
                EXISTS (
                    SELECT 1
                    FROM notations n
                    WHERE n.id = c.notation
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
                      AND d.notation_id = c.notation
                      AND (
                        m.owner = :currentUserId
                        OR EXISTS (
                            SELECT 1
                            FROM v_resource_grants rg
                            WHERE rg.resource_type = 'MODEL'
                              AND rg.resource_id = m.id
                              AND rg.permission IN ('VIEW', 'EDIT')
                              AND (rg.grantee_user_id = :currentUserId OR rg.grantee_user_id IS NULL)
                        )
                      )
                )
                OR (
                  :diagramEditorModelId IS NOT NULL
                  AND :notationId IS NOT NULL
                  AND c.notation = :notationId
                  AND EXISTS (
                    SELECT 1
                    FROM models m
                    WHERE m.id = :diagramEditorModelId
                      AND m.deleted = false
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
        @Param("diagramEditorModelId") diagramEditorModelId: UUID?,
        pageable: Pageable
    ): Page<Components>

    @Query(
        value = """
            SELECT DISTINCT c.node_type
            FROM components c
            WHERE c.notation = :notationId
        """,
        nativeQuery = true
    )
    fun findDistinctNodeTypeIdsByNotationId(@Param("notationId") notationId: UUID): List<UUID>

    fun existsByNodeTypeIdAndNotationIdIn(nodeTypeId: UUID, notationIds: Collection<UUID>): Boolean

    /** Тип узла используется компонентом в «видимой» нотации (прямой шаринг или диаграмма в доступной модели). */
    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                FROM components c
                INNER JOIN notations n ON n.id = c.notation
                WHERE c.node_type = :nodeTypeId
                  AND n.deleted = false
                  AND (
                      n.owner = :userId
                      OR EXISTS (
                          SELECT 1
                          FROM resource_shares rs
                          WHERE rs.resource_type = 'NOTATION'
                            AND rs.resource_id = n.id
                            AND rs.permission IN ('VIEW', 'EDIT')
                            AND (rs.grantee_user_id = :userId OR rs.grantee_user_id IS NULL)
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM diagrams d
                          INNER JOIN models m ON m.id = d.model
                          WHERE d.deleted = false
                            AND d.notation_id = n.id
                            AND m.deleted = false
                            AND (
                                m.owner = :userId
                                OR EXISTS (
                                    SELECT 1
                                    FROM resource_shares rs2
                                    WHERE rs2.resource_type = 'MODEL'
                                      AND rs2.resource_id = m.id
                                      AND rs2.permission IN ('VIEW', 'EDIT')
                                      AND (rs2.grantee_user_id = :userId OR rs2.grantee_user_id IS NULL)
                                )
                            )
                      )
                  )
            )
        """,
        nativeQuery = true
    )
    fun existsNodeTypeReachableViaViewableNotation(
        @Param("nodeTypeId") nodeTypeId: UUID,
        @Param("userId") userId: UUID
    ): Boolean
}


