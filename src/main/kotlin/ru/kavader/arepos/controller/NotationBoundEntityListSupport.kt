package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import ru.kavader.arepos.security.ResourceAccessService
import java.util.*

object NotationBoundEntityListSupport {
    fun <T> list(
        accessService: ResourceAccessService,
        pageable: Pageable,
        notationId: UUID?,
        modelId: UUID?,
        ownerId: UUID?,
        name: String?,
        tagsAll: String?,
        findAccessibleForUser: (
            notationId: UUID?,
            ownerId: UUID?,
            name: String?,
            tagsJson: String?,
            currentUserId: UUID,
            diagramEditorModelId: UUID?,
            pageable: Pageable
        ) -> Page<T>,
        findByFilters: (
            notationId: UUID?,
            ownerId: UUID?,
            name: String?,
            tagsJson: String?,
            pageable: Pageable
        ) -> Page<T>
    ): Page<T> {
        val normalizedName = name.trimmedOrNull()
        val tagsJson = parseCommaSeparatedTags(tagsAll).toTagsJsonArrayOrNull()
        return accessService.listPageWithAdminBypass(
            adminQuery = {
                findByFilters(notationId, ownerId, normalizedName, tagsJson, pageable)
            },
            userQuery = { currentUserId ->
                findAccessibleForUser(
                    notationId,
                    ownerId,
                    normalizedName,
                    tagsJson,
                    currentUserId,
                    modelId,
                    pageable
                )
            }
        )
    }
}
