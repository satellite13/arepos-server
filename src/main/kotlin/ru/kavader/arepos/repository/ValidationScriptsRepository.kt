package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.model.ValidationScripts
import java.util.*

@Repository
interface ValidationScriptsRepository : JpaRepository<ValidationScripts, UUID> {
    fun findByOwner(owner: Users, pageable: Pageable): Page<ValidationScripts>

    fun findByOwnerAndNameIgnoreCase(owner: Users, name: String): ValidationScripts?
}
