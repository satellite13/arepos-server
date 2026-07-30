package ru.kavader.arepos.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.Users
import java.util.*

@Repository
interface UsersRepository : JpaRepository<Users, UUID> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): Users?
    fun findByEmailIgnoreCase(email: String): Users?
    fun findByEmailContainingIgnoreCase(email: String, pageable: Pageable): Page<Users>
    fun findByEmailContainingIgnoreCaseAndRoleNot(email: String, role: Role, pageable: Pageable): Page<Users>
    fun findByOidcSub(oidcSub: String): Users?
    fun findByOidcSubContainingIgnoreCase(oidcSub: String, pageable: Pageable): Page<Users>

    @Query(
        "SELECT u FROM Users u WHERE LOWER(email) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR (u.oidcSub IS NOT NULL AND LOWER(u.oidcSub) LIKE LOWER(CONCAT('%', :search, '%')))"
    )
    fun searchByEmailOrOidcSubContaining(search: String, pageable: Pageable): Page<Users>
}
