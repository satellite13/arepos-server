package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.RelationRules
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.repository.sql.RelationRulesFilterSql
import ru.kavader.arepos.repository.sql.RelationRulesVisibilitySql
import java.util.*

@Repository
interface RelationRulesRepository : JpaRepository<RelationRules, UUID> {
    fun findByRelation(relation: Relations, pageable: Pageable): Page<RelationRules>
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
        value = RelationRulesFilterSql.FIND_ENTITY,
        countQuery = RelationRulesFilterSql.COUNT,
        nativeQuery = true
    )
    fun findByFilters(
        @Param("relationId") relationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("notationId") notationId: UUID?,
        pageable: Pageable
    ): Page<RelationRules>

    @Query(
        value = RelationRulesFilterSql.FIND_PROJECTED,
        countQuery = RelationRulesFilterSql.COUNT,
        nativeQuery = true
    )
    fun findProjectedByFilters(
        @Param("relationId") relationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("notationId") notationId: UUID?,
        pageable: Pageable
    ): Page<RelationRuleListProjection>

    @Query(
        value = RelationRulesFilterSql.FIND_PROJECTED_LIGHT,
        countQuery = RelationRulesFilterSql.COUNT,
        nativeQuery = true
    )
    fun findProjectedLightByFilters(
        @Param("relationId") relationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("notationId") notationId: UUID?,
        pageable: Pageable
    ): Page<RelationRuleListLightProjection>

    @Query(
        value = RelationRulesVisibilitySql.FIND_PROJECTED_FOR_USER,
        countQuery = RelationRulesVisibilitySql.COUNT_FOR_USER,
        nativeQuery = true
    )
    fun findProjectedByFiltersForUser(
        @Param("relationId") relationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("notationId") notationId: UUID?,
        @Param("currentUserId") currentUserId: UUID,
        @Param("diagramEditorModelId") diagramEditorModelId: UUID?,
        pageable: Pageable
    ): Page<RelationRuleListProjection>

    @Query(
        value = RelationRulesVisibilitySql.FIND_PROJECTED_LIGHT_FOR_USER,
        countQuery = RelationRulesVisibilitySql.COUNT_FOR_USER,
        nativeQuery = true
    )
    fun findProjectedLightByFiltersForUser(
        @Param("relationId") relationId: UUID?,
        @Param("ownerId") ownerId: UUID?,
        @Param("notationId") notationId: UUID?,
        @Param("currentUserId") currentUserId: UUID,
        @Param("diagramEditorModelId") diagramEditorModelId: UUID?,
        pageable: Pageable
    ): Page<RelationRuleListLightProjection>
}
