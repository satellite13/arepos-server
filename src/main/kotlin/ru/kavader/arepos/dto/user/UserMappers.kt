package ru.kavader.arepos.dto.user

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.auth.UserInfoResponse
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.service.UserProfileAttrsService

@Component
class UserMapper(
    private val profileService: UserProfileAttrsService
) {
    fun ownerDisplayName(user: Users): String {
        val profile = profileService.readProfile(user.attrs)
        val parts = listOfNotNull(
            profile.firstName?.trim()?.takeIf { it.isNotEmpty() },
            profile.lastName?.trim()?.takeIf { it.isNotEmpty() }
        )
        return parts.joinToString(" ").ifEmpty { user.email }
    }

    fun toResponse(user: Users): UserResponse {
        val profile = profileService.readProfile(user.attrs)
        return UserResponse(
            id = requireNotNull(user.id),
            email = user.email,
            role = user.role.name,
            isActive = user.isActive,
            firstName = profile.firstName,
            lastName = profile.lastName,
            middleName = profile.middleName,
            position = profile.position,
            attrs = user.attrs,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }

    fun toPublicResponse(user: Users): UserPublicResponse {
        val profile = profileService.readProfile(user.attrs)
        return UserPublicResponse(
            id = requireNotNull(user.id),
            email = user.email,
            firstName = profile.firstName,
            lastName = profile.lastName,
            middleName = profile.middleName,
            position = profile.position
        )
    }

    fun toUserInfoResponse(user: Users): UserInfoResponse {
        val profile = profileService.readProfile(user.attrs)
        return UserInfoResponse(
            id = requireNotNull(user.id),
            email = user.email,
            role = user.role.name,
            firstName = profile.firstName,
            lastName = profile.lastName,
            middleName = profile.middleName,
            position = profile.position,
            attrs = profileService.serializeProfile(profile),
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}
