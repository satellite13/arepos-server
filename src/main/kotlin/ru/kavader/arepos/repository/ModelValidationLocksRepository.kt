package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.ModelValidationLocks
import java.util.UUID

@Repository
interface ModelValidationLocksRepository : JpaRepository<ModelValidationLocks, UUID> {

    fun findByModelId(modelId: UUID): ModelValidationLocks?

    fun deleteByModelIdAndLockedBy_Id(modelId: UUID, lockedById: UUID): Long
}
