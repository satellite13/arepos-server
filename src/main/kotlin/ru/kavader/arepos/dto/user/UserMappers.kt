package ru.kavader.arepos.dto.user

import org.springframework.stereotype.Component
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.service.UserProfileAttrsService

@Component
class UserMapper(
    private val profileService: UserProfileAttrsService
) {
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
}
