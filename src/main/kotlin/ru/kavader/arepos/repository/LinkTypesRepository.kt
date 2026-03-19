package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.LinkTypes
import ru.kavader.arepos.model.Users
import java.util.UUID

@Repository
interface LinkTypesRepository : JpaRepository<LinkTypes, UUID> {
    fun findByOwner(owner: Users, pageable: Pageable): Page<LinkTypes>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<LinkTypes>
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<LinkTypes>
    fun findByNameIgnoreCase(name: String): LinkTypes?
}


