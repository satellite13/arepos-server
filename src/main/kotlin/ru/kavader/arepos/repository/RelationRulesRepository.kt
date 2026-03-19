package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Users
import java.util.UUID

@Repository
interface RelationRulesRepository : JpaRepository<RelationRules, UUID> {
    fun findByRelation(relation: Relations, pageable: Pageable): Page<RelationRules>
    fun findByOwner(owner: Users, pageable: Pageable): Page<RelationRules>
    fun findByRelationAndOwner(relation: Relations, owner: Users, pageable: Pageable): Page<RelationRules>
    fun existsByRelationAndFromComponentAndToComponent(
        relation: Relations,
        fromComponent: ru.kavader.arepos.model.Components,
        toComponent: ru.kavader.arepos.model.Components
    ): Boolean
    fun existsByRelationAndFromComponentAndToComponentAndIdNot(
        relation: Relations,
        fromComponent: ru.kavader.arepos.model.Components,
        toComponent: ru.kavader.arepos.model.Components,
        id: UUID
    ): Boolean

    @Query(
        value = """
            SELECT rr.*
            FROM relation_rules rr
            JOIN relations r ON rr.relation = r.id
            JOIN components c_from ON rr.from_component = c_from.id
            JOIN components c_to ON rr.to_component = c_to.id
            WHERE (:relationId IS NULL OR rr.relation = :relationId)
              AND (:ownerId IS NULL OR rr.owner = :ownerId)
              AND (
                :notationId IS NULL OR (
                  r.notation = :notationId AND
                  c_from.notation = :notationId AND
                  c_to.notation = :notationId
                )
              )
            ORDER BY rr.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM relation_rules rr
            JOIN relations r ON rr.relation = r.id
            JOIN components c_from ON rr.from_component = c_from.id
            JOIN components c_to ON rr.to_component = c_to.id
            WHERE (:relationId IS NULL OR rr.relation = :relationId)
              AND (:ownerId IS NULL OR rr.owner = :ownerId)
              AND (
                :notationId IS NULL OR (
                  r.notation = :notationId AND
                  c_from.notation = :notationId AND
                  c_to.notation = :notationId
                )
              )
        """,
        nativeQuery = true
    )
    fun findByFilters(
        @Param("relationId") relationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("notationId") notationId: UUID?,
        pageable: Pageable
    ): Page<RelationRules>

    @Query(
        value = """
            SELECT
                rr.id AS id,
                rr.relation AS relationId,
                rr.from_component AS fromComponentId,
                rr.to_component AS toComponentId,
                rr.owner AS ownerId,
                rr.attrs AS attrs,
                rr.created_at AS createdAt,
                rr.updated_at AS updatedAt
            FROM relation_rules rr
            JOIN relations r ON rr.relation = r.id
            JOIN components c_from ON rr.from_component = c_from.id
            JOIN components c_to ON rr.to_component = c_to.id
            WHERE (:relationId IS NULL OR rr.relation = :relationId)
              AND (:ownerId IS NULL OR rr.owner = :ownerId)
              AND (
                :notationId IS NULL OR (
                  r.notation = :notationId AND
                  c_from.notation = :notationId AND
                  c_to.notation = :notationId
                )
              )
            ORDER BY rr.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM relation_rules rr
            JOIN relations r ON rr.relation = r.id
            JOIN components c_from ON rr.from_component = c_from.id
            JOIN components c_to ON rr.to_component = c_to.id
            WHERE (:relationId IS NULL OR rr.relation = :relationId)
              AND (:ownerId IS NULL OR rr.owner = :ownerId)
              AND (
                :notationId IS NULL OR (
                  r.notation = :notationId AND
                  c_from.notation = :notationId AND
                  c_to.notation = :notationId
                )
              )
        """,
        nativeQuery = true
    )
    fun findProjectedByFilters(
        @Param("relationId") relationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("notationId") notationId: UUID?,
        pageable: Pageable
    ): Page<RelationRuleListProjection>

    @Query(
        value = """
            SELECT
                rr.id AS id,
                rr.relation AS relationId,
                rr.from_component AS fromComponentId,
                rr.to_component AS toComponentId,
                rr.owner AS ownerId,
                rr.attrs AS attrs,
                rr.created_at AS createdAt,
                rr.updated_at AS updatedAt
            FROM relation_rules rr
            JOIN relations r ON rr.relation = r.id
            JOIN components c_from ON rr.from_component = c_from.id
            JOIN components c_to ON rr.to_component = c_to.id
            WHERE (:relationId IS NULL OR rr.relation = :relationId)
              AND (:ownerId IS NULL OR rr.owner = :ownerId)
              AND (
                :notationId IS NULL OR (
                  r.notation = :notationId AND
                  c_from.notation = :notationId AND
                  c_to.notation = :notationId
                )
              )
              AND (
                r.notation IN (
                  SELECT n.id
                  FROM notations n
                  WHERE
                    n.owner = :currentUserId
                    OR EXISTS (
                      SELECT 1
                      FROM resource_shares rs
                      WHERE rs.resource_type = 'NOTATION'
                        AND rs.resource_id = n.id
                        AND rs.permission IN ('VIEW', 'EDIT')
                        AND (rs.grantee_user_id = :currentUserId OR rs.grantee_user_id IS NULL)
                    )
                )
                OR r.notation IN (
                  SELECT DISTINCT d.notation
                  FROM diagrams d
                  JOIN models m ON d.model = m.id
                  WHERE
                    m.owner = :currentUserId
                    OR EXISTS (
                      SELECT 1
                      FROM resource_shares rs
                      WHERE rs.resource_type = 'MODEL'
                        AND rs.resource_id = m.id
                        AND rs.permission = 'EDIT'
                        AND (rs.grantee_user_id = :currentUserId OR rs.grantee_user_id IS NULL)
                    )
                )
              )
            ORDER BY rr.id
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM relation_rules rr
            JOIN relations r ON rr.relation = r.id
            JOIN components c_from ON rr.from_component = c_from.id
            JOIN components c_to ON rr.to_component = c_to.id
            WHERE (:relationId IS NULL OR rr.relation = :relationId)
              AND (:ownerId IS NULL OR rr.owner = :ownerId)
              AND (
                :notationId IS NULL OR (
                  r.notation = :notationId AND
                  c_from.notation = :notationId AND
                  c_to.notation = :notationId
                )
              )
              AND (
                r.notation IN (
                  SELECT n.id
                  FROM notations n
                  WHERE
                    n.owner = :currentUserId
                    OR EXISTS (
                      SELECT 1
                      FROM resource_shares rs
                      WHERE rs.resource_type = 'NOTATION'
                        AND rs.resource_id = n.id
                        AND rs.permission IN ('VIEW', 'EDIT')
                        AND (rs.grantee_user_id = :currentUserId OR rs.grantee_user_id IS NULL)
                    )
                )
                OR r.notation IN (
                  SELECT DISTINCT d.notation
                  FROM diagrams d
                  JOIN models m ON d.model = m.id
                  WHERE
                    m.owner = :currentUserId
                    OR EXISTS (
                      SELECT 1
                      FROM resource_shares rs
                      WHERE rs.resource_type = 'MODEL'
                        AND rs.resource_id = m.id
                        AND rs.permission = 'EDIT'
                        AND (rs.grantee_user_id = :currentUserId OR rs.grantee_user_id IS NULL)
                    )
                )
              )
        """,
        nativeQuery = true
    )
    fun findProjectedByFiltersForUser(
        @Param("relationId") relationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("notationId") notationId: UUID?,
        @Param("currentUserId") currentUserId: UUID,
        pageable: Pageable
    ): Page<RelationRuleListProjection>
}


