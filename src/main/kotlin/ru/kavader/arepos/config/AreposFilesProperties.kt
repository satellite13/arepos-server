package ru.kavader.arepos.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "arepos.files")
data class AreposFilesProperties(
    /** File storage backend: `minio` or `disabled`. */
    val storage: String = "minio"
)
