package ru.kavader.arepos.security

import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

object CurrentUser {

    fun getId(): UUID? {
        return SecurityContextHolder.getContext().authentication?.principal as? UUID
    }

    fun getRole(): String? {
        return SecurityContextHolder.getContext().authentication?.authorities
            ?.firstOrNull()?.authority?.removePrefix("ROLE_")
    }

}
