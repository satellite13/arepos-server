package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.notation.NotationBoundEntityUpdateRequest
import ru.kavader.arepos.model.NotationBoundEntity
import ru.kavader.arepos.model.Notations
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.service.MdFileLinkValidator

object NotationBoundEntityWriteSupport {
    fun applyPartialUpdate(
        entity: NotationBoundEntity,
        request: NotationBoundEntityUpdateRequest,
        owner: Users,
        notation: Notations,
        mdFileLinkValidator: MdFileLinkValidator
    ) {
        mdFileLinkValidator.validate(request.attrs)
        entity.name = request.name ?: entity.name
        entity.attrs = request.attrs ?: entity.attrs
        entity.version = request.version ?: entity.version
        entity.owner = owner
        entity.notation = notation
    }

    fun <T, R> persistUpdate(
        entity: T,
        request: NotationBoundEntityUpdateRequest,
        owner: Users,
        notation: Notations,
        mdFileLinkValidator: MdFileLinkValidator,
        applyExtra: T.() -> Unit,
        save: (T) -> T,
        toResponse: (T) -> R
    ): R where T : NotationBoundEntity {
        applyPartialUpdate(entity, request, owner, notation, mdFileLinkValidator)
        entity.applyExtra()
        return toResponse(save(entity))
    }
}
