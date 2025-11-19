package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
}


