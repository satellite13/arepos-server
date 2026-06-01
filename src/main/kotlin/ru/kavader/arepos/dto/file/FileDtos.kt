package ru.kavader.arepos.dto.file

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
    val content: String,
    val filename: String = "documentation.md"
)
