package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Models
import ru.kavader.arepos.model.OefMergeDecisions
import java.util.UUID

@Repository
interface OefMergeDecisionsRepository : JpaRepository<OefMergeDecisions, UUID> {

    fun findByModel(model: Models): List<OefMergeDecisions>

    fun findByModelAndOefEntityId(model: Models, oefEntityId: String): OefMergeDecisions?

    fun deleteByModelAndOefEntityId(model: Models, oefEntityId: String): Long
}
