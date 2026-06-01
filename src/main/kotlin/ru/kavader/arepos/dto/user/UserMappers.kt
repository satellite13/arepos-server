package ru.kavader.arepos.dto.user

import ru.kavader.arepos.model.Users
import ru.kavader.arepos.service.UserProfileAttrsService

fun Users.toResponse(profileService: UserProfileAttrsService): UserResponse {
    val profile = profileService.readProfile(attrs)
    return UserResponse(
        id = requireNotNull(id),
        email = email,
        role = role.name,
        isActive = isActive,
        firstName = profile.firstName,
        lastName = profile.lastName,
        middleName = profile.middleName,
        position = profile.position,
        attrs = attrs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Users.toPublicResponse(profileService: UserProfileAttrsService): UserPublicResponse {
    val profile = profileService.readProfile(attrs)
    return UserPublicResponse(
        id = requireNotNull(id),
        email = email,
        firstName = profile.firstName,
        lastName = profile.lastName,
        middleName = profile.middleName,
        position = profile.position
    )
}
