package ru.kavader.arepos.controller

import ru.kavader.arepos.dto.notation.CatalogTypeUpdateRequest
import ru.kavader.arepos.model.CatalogTypeEntity
import ru.kavader.arepos.security.OwnerResolutionService
import ru.kavader.arepos.service.MdFileLinkValidator

object CatalogTypeWriteSupport {
    fun <T, R> persistUpdate(
        entity: T,
        request: CatalogTypeUpdateRequest,
        ownerResolutionService: OwnerResolutionService,
        mdFileLinkValidator: MdFileLinkValidator,
        save: (T) -> T,
        toResponse: (T) -> R
    ): R where T : CatalogTypeEntity {
        mdFileLinkValidator.validate(request.attrs)
        entity.name = request.name ?: entity.name
        entity.attrs = request.attrs ?: entity.attrs
        entity.owner = ownerResolutionService.resolveOwnerForUpdate(request.ownerId, entity.owner)
        return toResponse(save(entity))
    }
}
