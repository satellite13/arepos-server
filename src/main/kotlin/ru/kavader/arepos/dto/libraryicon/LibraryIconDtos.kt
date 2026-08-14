package ru.kavader.arepos.dto.libraryicon

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LibraryIconResponse(
    val id: UUID,
    val name: String,
    val svg: String,
    val contentHash: String,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class LibraryIconCreateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 102_400)
    val svg: String
)

data class LibraryIconBatchCreateRequest(
    @field:Size(max = 100)
    @field:Valid
    val icons: List<LibraryIconCreateRequest> = emptyList()
)

data class LibraryIconBundleItem(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 102_400)
    val svg: String
)

data class LibraryIconBundle(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String? = null,
    @field:Size(max = 500)
    @field:Valid
    val icons: List<LibraryIconBundleItem> = emptyList()
) {
    companion object {
        const val FORMAT = "warchi-icon-bundle"
        const val VERSION = 1
    }
}

data class LibraryIconBundleImportResult(
    val created: Int,
    val overwritten: Int
)
