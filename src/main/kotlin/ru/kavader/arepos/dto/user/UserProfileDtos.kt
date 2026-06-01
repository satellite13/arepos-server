package ru.kavader.arepos.dto.user

data class UserProfileData(
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val position: String?
)

data class UserProfilePatch(
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val position: String?
)
