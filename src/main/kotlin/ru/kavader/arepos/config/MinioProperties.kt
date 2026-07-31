package ru.kavader.arepos.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "arepos.files.minio")
data class MinioProperties(
    val endpoint: String = "http://localhost:9000",
    val accessKey: String = "minioadmin",
    val secretKey: String = "minioadmin",
    val bucket: String = "arepos-files",
    /** S3 region; required for Yandex Object Storage (`ru-central1`). Empty = MinIO default discovery. */
    val region: String = "",
    val secure: Boolean = false
)
