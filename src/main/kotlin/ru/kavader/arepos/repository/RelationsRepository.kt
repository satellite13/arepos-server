package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Relations
import ru.kavader.arepos.model.Users
import java.util.UUID

@Repository
interface RelationsRepository : JpaRepository<Relations, UUID> {
    fun findByNotation(notation: Notations, pageable: Pageable): Page<Relations>
    fun findByOwner(owner: Users, pageable: Pageable): Page<Relations>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Relations>
    fun findByNotationAndNameContainingIgnoreCase(notation: Notations, name: String, pageable: Pageable): Page<Relations>
    fun findByOwnerAndNameContainingIgnoreCase(owner: Users, name: String, pageable: Pageable): Page<Relations>
}


