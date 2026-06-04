package ru.kavader.arepos.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.user.UserProfileData
import ru.kavader.arepos.dto.user.UserProfilePatch

private const val FIRST_NAME_KEY = "firstName"
private const val LAST_NAME_KEY = "lastName"
private const val MIDDLE_NAME_KEY = "middleName"
private const val POSITION_KEY = "position"
private val PATH_UNSAFE_PATTERN = Regex("""\.\.|/|\\""")

@Component
class UserProfileAttrsService(
    private val objectMapper: ObjectMapper
) {
    private val mapType = object : TypeReference<MutableMap<String, Any?>>() {}

    fun readProfile(attrs: String?): UserProfileData {
        val parsed = parseAttrs(attrs)
        return UserProfileData(
            firstName = sanitizeTextFieldLenient(parsed[FIRST_NAME_KEY]?.toString()),
            lastName = sanitizeTextFieldLenient(parsed[LAST_NAME_KEY]?.toString()),
            middleName = sanitizeTextFieldLenient(parsed[MIDDLE_NAME_KEY]?.toString()),
            position = sanitizeTextFieldLenient(parsed[POSITION_KEY]?.toString())
        )
    }

    fun serializeProfile(profile: UserProfileData): String? {
        val map = linkedMapOf<String, String>()
        profile.firstName?.let { map[FIRST_NAME_KEY] = it }
        profile.lastName?.let { map[LAST_NAME_KEY] = it }
        profile.middleName?.let { map[MIDDLE_NAME_KEY] = it }
        profile.position?.let { map[POSITION_KEY] = it }
        return if (map.isEmpty()) null else objectMapper.writeValueAsString(map)
    }

    fun mergeProfile(existingAttrs: String?, patch: UserProfilePatch): String? {
        val attrsMap = parseAttrs(existingAttrs)

        patch.firstName?.let {
            attrsMap[FIRST_NAME_KEY] = sanitizeTextField(requireNonBlank(it, "firstName"), "firstName")
        }
        patch.lastName?.let {
            attrsMap[LAST_NAME_KEY] = sanitizeTextField(requireNonBlank(it, "lastName"), "lastName")
        }
        patch.position?.let {
            val value = it.trim()
            if (value.isEmpty()) {
                attrsMap.remove(POSITION_KEY)
            } else {
                attrsMap[POSITION_KEY] = sanitizeTextField(value, "position")
            }
        }
        patch.middleName?.let {
            val value = it.trim()
            if (value.isEmpty()) {
                attrsMap.remove(MIDDLE_NAME_KEY)
            } else {
                attrsMap[MIDDLE_NAME_KEY] = sanitizeTextField(value, "middleName")
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

    private fun sanitizeTextField(value: String, fieldName: String): String {
        if (PATH_UNSAFE_PATTERN.containsMatchIn(value)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "$fieldName must not contain path characters"
            )
        }
        return value
    }

    private fun sanitizeTextFieldLenient(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return PATH_UNSAFE_PATTERN.replace(trimmed, "").trim().ifEmpty { null }
    }
}
