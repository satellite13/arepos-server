package ru.kavader.arepos.featuregrant

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.model.Role
import ru.kavader.arepos.model.RoleFeatureGrants
import ru.kavader.arepos.model.UserFeatureGrantAllows
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.RoleFeatureGrantsRepository
import ru.kavader.arepos.repository.UserFeatureGrantAllowsRepository
import java.util.UUID

@Service
class FeatureGrantService(
    private val roleRepo: RoleFeatureGrantsRepository,
    private val userAllowRepo: UserFeatureGrantAllowsRepository,
) {
    fun effectiveGrants(user: Users): List<String> {
        if (user.role == Role.admin) return FeatureGrantKeys.ALL
        val roleKeys = roleRepo.findByRole(user.role.name).map { it.grantKey }
        val allows = userAllowRepo.findByUserId(requireNotNull(user.id)).map { it.grantKey }
        return (roleKeys + allows).distinct().sorted()
    }

    @Transactional
    fun replaceRoleGrants(role: Role, keys: List<String>) {
        if (role == Role.admin) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "admin grants are fixed")
        }
        val distinctKeys = validateKnownKeys(keys)
        roleRepo.deleteByRole(role.name)
        roleRepo.saveAll(
            distinctKeys.map { RoleFeatureGrants(role = role.name, grantKey = it) }
        )
    }

    @Transactional
    fun replaceUserAllows(userId: UUID, keys: List<String>) {
        val distinctKeys = validateKnownKeys(keys)
        userAllowRepo.deleteByUserId(userId)
        userAllowRepo.saveAll(
            distinctKeys.map { UserFeatureGrantAllows(userId = userId, grantKey = it) }
        )
    }

    fun roleGrantsMatrix(): Map<String, List<String>> {
        return roleRepo.findAll()
            .asSequence()
            .filter { it.role != Role.admin.name }
            .groupBy({ it.role }, { it.grantKey })
            .mapValues { (_, grantKeys) -> grantKeys.distinct().sorted() }
            .toSortedMap()
    }

    fun userAllows(userId: UUID): List<String> {
        return userAllowRepo.findByUserId(userId).map { it.grantKey }.distinct().sorted()
    }

    private fun validateKnownKeys(keys: List<String>): List<String> {
        val distinctKeys = keys.distinct()
        val unknown = distinctKeys.filterNot { FeatureGrantKeys.isKnown(it) }
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown grants: $unknown")
        }
        return distinctKeys
    }
}
