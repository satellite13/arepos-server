package ru.kavader.arepos.config

import io.minio.MinioClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

@Configuration
@EnableConfigurationProperties(MinioProperties::class)
@ConditionalOnProperty(name = ["arepos.files.storage"], havingValue = "minio")
class MinioConfig(
    private val minioProperties: MinioProperties,
    private val environment: Environment
) {
    companion object {
        private val log = LoggerFactory.getLogger(MinioConfig::class.java)
        private const val DEFAULT_ACCESS_KEY = "minioadmin"
        private const val DEFAULT_SECRET_KEY = "minioadmin"
    }

    @Bean
    fun minioClient(): MinioClient {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            require(minioProperties.accessKey.isNotBlank()) {
                "MinIO access key must not be blank in prod profile"
            }
            require(minioProperties.secretKey.isNotBlank()) {
                "MinIO secret key must not be blank in prod profile"
            }
            require(minioProperties.accessKey != DEFAULT_ACCESS_KEY) {
                "MinIO access key must not use default value in prod profile"
            }
            require(minioProperties.secretKey != DEFAULT_SECRET_KEY) {
                "MinIO secret key must not use default value in prod profile"
            }
        }
        val endpoint = minioProperties.endpoint.trim().trimEnd('/')
        val accessKey = minioProperties.accessKey.trim()
        val secretKey = minioProperties.secretKey.trim()
        val region = minioProperties.region.trim()
        val builder = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
        if (region.isNotEmpty()) {
            builder.region(region)
        }
        val client = builder.build()
        // Yandex Object Storage is happiest with path-style URLs (host/bucket/key).
        client.disableVirtualStyleEndpoint()
        log.info(
            "MinIO client configured: endpoint={}, bucket={}, region={}, accessKeyLen={}, accessKeyPrefix={}",
            endpoint,
            minioProperties.bucket,
            region.ifEmpty { "<auto>" },
            accessKey.length,
            accessKey.take(4).ifEmpty { "<empty>" }
        )
        return client
    }
}
