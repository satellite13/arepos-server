package ru.kavader.arepos.controller

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

private const val FIRST_NAME_KEY = "firstName"
private const val LAST_NAME_KEY = "lastName"
private const val MIDDLE_NAME_KEY = "middleName"
private const val POSITION_KEY = "position"

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

@Component
class UserProfileAttrsService(
    private val objectMapper: ObjectMapper
) {
    private val mapType = object : TypeReference<MutableMap<String, Any?>>() {}

    fun readProfile(attrs: String?): UserProfileData {
        val parsed = parseAttrs(attrs)
        return UserProfileData(
            firstName = parsed[FIRST_NAME_KEY]?.toString(),
            lastName = parsed[LAST_NAME_KEY]?.toString(),
            middleName = parsed[MIDDLE_NAME_KEY]?.toString(),
            position = parsed[POSITION_KEY]?.toString()
        )
    }

    fun mergeProfile(existingAttrs: String?, patch: UserProfilePatch): String? {
        val attrsMap = parseAttrs(existingAttrs)

        patch.firstName?.let {
            attrsMap[FIRST_NAME_KEY] = requireNonBlank(it, "firstName")
        }
        patch.lastName?.let {
            attrsMap[LAST_NAME_KEY] = requireNonBlank(it, "lastName")
        }
        patch.position?.let {
            val value = it.trim()
            if (value.isEmpty()) {
                attrsMap.remove(POSITION_KEY)
            } else {
                attrsMap[POSITION_KEY] = value
            }
        }
        patch.middleName?.let {
            val value = it.trim()
            if (value.isEmpty()) {
                attrsMap.remove(MIDDLE_NAME_KEY)
            } else {
                attrsMap[MIDDLE_NAME_KEY] = value
            }
        }

        return if (attrsMap.isEmpty()) null else objectMapper.writeValueAsString(attrsMap)
    }

    fun buildProfileAttrs(
        firstName: String,
        lastName: String,
        middleName: String?,
        position: String?
    ): String {
        return mergeProfile(
            existingAttrs = null,
            patch = UserProfilePatch(
                firstName = firstName,
                lastName = lastName,
                middleName = middleName,
                position = position
            )
        ) ?: "{}"
    }

    private fun parseAttrs(attrs: String?): MutableMap<String, Any?> {
        if (attrs.isNullOrBlank()) return mutableMapOf()
        return try {
            objectMapper.readValue(attrs, mapType)
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun requireNonBlank(value: String, fieldName: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$fieldName must not be blank")
        }
        return trimmed
    }
}
