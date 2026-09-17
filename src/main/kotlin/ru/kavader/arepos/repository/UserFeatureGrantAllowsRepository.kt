package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.UserFeatureGrantAllows
import ru.kavader.arepos.model.UserFeatureGrantAllowsId
import java.util.UUID

@Repository
interface UserFeatureGrantAllowsRepository : JpaRepository<UserFeatureGrantAllows, UserFeatureGrantAllowsId> {
    fun findByUserId(userId: UUID): List<UserFeatureGrantAllows>

    fun deleteByUserId(userId: UUID): Long
}
