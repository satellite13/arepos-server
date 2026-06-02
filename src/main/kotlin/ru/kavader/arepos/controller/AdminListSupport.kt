package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

fun <T> ResourceAccessService.listPageWithAdminBypass(
    pageable: Pageable,
    adminQuery: () -> Page<T>,
    userQuery: (currentUserId: UUID) -> Page<T>
): Page<T> =
    if (canViewAdminPanel()) {
        adminQuery()
    } else {
        userQuery(currentUserId())
    }

fun <T, R> ResourceAccessService.listPageWithAdminBypass(
    pageable: Pageable,
    adminQuery: () -> Page<T>,
    userQuery: (currentUserId: UUID) -> Page<T>,
    map: (T) -> R
): Page<R> =
    listPageWithAdminBypass(pageable, adminQuery, userQuery).map(map)
