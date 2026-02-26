package ru.kavader.arepos.config

import io.minio.MinioClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MinioProperties::class)
@ConditionalOnProperty(name = ["arepos.files.storage"], havingValue = "minio")
class MinioConfig(
    private val minioProperties: MinioProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(MinioConfig::class.java)
    }

    @Bean
    fun minioClient(): MinioClient {
        val client = MinioClient.builder()
            .endpoint(minioProperties.endpoint)
            .credentials(minioProperties.accessKey, minioProperties.secretKey)
            .build()
        log.info("MinIO client configured: endpoint={}, bucket={}", minioProperties.endpoint, minioProperties.bucket)
        return client
    }
}
