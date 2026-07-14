package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.security.ResourceAccessService
import java.util.*

data class OwnerNamePageQueries<T>(
    val byOwnerAndName: (Users, String, Pageable) -> Page<T>,
    val byOwner: (Users, Pageable) -> Page<T>,
    val byName: (String, Pageable) -> Page<T>,
    val all: (Pageable) -> Page<T>
)

fun <T> listPageByOwnerAndName(
    effectiveOwner: Users?,
    name: String?,
    pageable: Pageable,
    queries: OwnerNamePageQueries<T>
): Page<T> =
    when {
        effectiveOwner != null && name != null -> queries.byOwnerAndName(effectiveOwner, name, pageable)
        effectiveOwner != null -> queries.byOwner(effectiveOwner, pageable)
        name != null -> queries.byName(name, pageable)
        else -> queries.all(pageable)
    }

fun <T> ResourceAccessService.listPageWithAdminBypass(
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
    listPageWithAdminBypass(adminQuery, userQuery).map(map)

fun <T, K : Any, R> Page<T>.mapWithPermissions(
    loadPermissions: (List<T>) -> Map<K, String?>,
    idOf: (T) -> K?,
    transform: (entity: T, permission: String?) -> R
): Page<R> {
    val permissions = loadPermissions(content)
    return map { entity ->
        val entityId = requireNotNull(idOf(entity)) { "Persisted entity ID must not be null" }
        transform(entity, permissions[entityId])
    }
}
