package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.RoleFeatureGrants
import ru.kavader.arepos.model.RoleFeatureGrantsId

@Repository
interface RoleFeatureGrantsRepository : JpaRepository<RoleFeatureGrants, RoleFeatureGrantsId> {
    fun findByRole(role: String): List<RoleFeatureGrants>

    fun deleteByRole(role: String): Long
}
