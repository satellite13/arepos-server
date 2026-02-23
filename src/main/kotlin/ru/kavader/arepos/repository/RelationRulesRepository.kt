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
}


