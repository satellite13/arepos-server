package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.ApiKeys
import ru.kavader.arepos.model.Users
import java.util.*

@Repository
interface ApiKeysRepository : JpaRepository<ApiKeys, UUID> {
    fun findByOwnerOrderByCreatedAtDesc(owner: Users): List<ApiKeys>

    fun findByIdAndOwner(id: UUID, owner: Users): ApiKeys?

    fun findByTokenHash(tokenHash: String): ApiKeys?
}
