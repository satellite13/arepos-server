package ru.kavader.arepos.dto.file

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class FileUploadResponse(
    val id: UUID,
    val url: String,
    val filename: String,
    val contentType: String,
    val size: Long
)

data class FileVersionResponse(
    val versionNumber: Int,
    val createdAt: Instant,
    val createdBy: UUID,
    val size: Long
)

data class UploadMarkdownRequest(
    @field:Size(max = 5_000_000)
    val content: String,
    @field:NotBlank
    @field:Size(max = 255)
    val filename: String = "documentation.md"
)
