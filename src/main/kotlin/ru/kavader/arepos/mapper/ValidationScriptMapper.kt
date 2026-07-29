package ru.kavader.arepos.mapper

import org.springframework.stereotype.Component
import ru.kavader.arepos.dto.script.ValidationScriptResponse
import ru.kavader.arepos.model.ValidationScripts
import ru.kavader.arepos.security.ResourceAccessService

@Component
class ValidationScriptMapper(
    private val accessService: ResourceAccessService
) {
    fun toResponse(script: ValidationScripts, accessPermission: String?): ValidationScriptResponse =
        ValidationScriptResponse(
            id = script.id!!,
            name = script.name,
            description = script.description,
            source = script.source,
            ownerId = script.owner.id!!,
            accessPermission = accessPermission,
            attrs = script.attrs,
            createdAt = script.createdAt,
            updatedAt = script.updatedAt
        )

    fun toResponse(script: ValidationScripts): ValidationScriptResponse =
        toResponse(script, accessService.validationScriptAccessPermission(script))
}
