package ru.kavader.arepos.dto.notation

import java.util.*

interface CatalogTypeUpdateRequest {
    val name: String?
    val ownerId: UUID?
    val attrs: String?
}
