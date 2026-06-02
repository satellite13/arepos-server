package ru.kavader.arepos.config

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component("minio")
@ConditionalOnBean(MinioClient::class)
@ConditionalOnProperty(name = ["arepos.files.storage"], havingValue = "minio")
class MinioHealthIndicator(
    private val minioClient: MinioClient,
    private val minioProperties: MinioProperties
) : HealthIndicator {
    override fun health(): Health {
        return try {
            val exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(minioProperties.bucket)
                    .build()
            )
            if (exists) {
                Health.up()
                    .withDetail("bucket", minioProperties.bucket)
                    .withDetail("endpoint", minioProperties.endpoint)
                    .build()
            } else {
                Health.down()
                    .withDetail("bucket", minioProperties.bucket)
                    .withDetail("reason", "Bucket does not exist")
                    .build()
            }
        } catch (ex: Exception) {
            Health.down(ex)
                .withDetail("bucket", minioProperties.bucket)
                .withDetail("endpoint", minioProperties.endpoint)
                .build()
        }
    }
}
