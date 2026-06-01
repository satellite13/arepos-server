package ru.kavader.arepos.security

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.repository.UsersRepository
import java.util.UUID

@Service
class OwnerResolutionService(
    private val usersRepository: UsersRepository,
    private val accessService: ResourceAccessService
) {
    fun currentUserId(): UUID = CurrentUser.getId()
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")

    /**
     * Resolves the owner for entity creation. Admins may assign any owner;
     * other users always create under their own identity.
     */
    fun resolveOwnerForCreate(requestOwnerId: UUID?): ru.kavader.arepos.model.Users {
        val userId = currentUserId()
        val resolvedOwnerId = if (accessService.canViewAdminPanel()) {
            requestOwnerId ?: userId
        } else {
            userId
        }
        return usersRepository.findById(resolvedOwnerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $resolvedOwnerId not found")
        }
    }

    /**
     * Resolves the owner for entity update. Admins may reassign ownership;
     * other users always keep the existing owner.
     */
    fun resolveOwnerForUpdate(requestOwnerId: UUID?, currentOwner: ru.kavader.arepos.model.Users): ru.kavader.arepos.model.Users {
        return if (accessService.canViewAdminPanel()) {
            requestOwnerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            } ?: currentOwner
        } else {
            currentOwner
        }
    }

    /**
     * Resolves a readable owner for list filtering. Admins may filter by any owner
     * or see all; other users must either view their own resources or have shared access
     * from the requested owner via the [hasSharedAccess] predicate.
     *
     * Returns the [Users] entity for the resolved owner, or null if no filter should be applied.
     */
    fun resolveReadableOwner(
        ownerId: UUID?,
        hasSharedAccess: (ownerId: UUID, userId: UUID) -> Boolean
    ): ru.kavader.arepos.model.Users? {
        val currentUserId = currentUserId()

        if (accessService.canViewAdminPanel()) {
            return ownerId?.let {
                usersRepository.findById(it).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Owner $it not found")
                }
            }
        }

        if (ownerId != null && ownerId != currentUserId) {
            if (!hasSharedAccess(ownerId, currentUserId)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            }
            return null
        }

        return usersRepository.findById(currentUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Current user $currentUserId not found")
        }
    }
}
