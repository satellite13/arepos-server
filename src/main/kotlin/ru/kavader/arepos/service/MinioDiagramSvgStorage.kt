package ru.kavader.arepos.service

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import ru.kavader.arepos.config.MinioProperties
import java.io.ByteArrayInputStream
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["arepos.files.storage"], havingValue = "minio")
class MinioDiagramSvgStorage(
    private val minioClient: MinioClient,
    private val minioProperties: MinioProperties
) : DiagramSvgStorage {

    override fun putSvg(diagramId: UUID, svgContent: String): Boolean {
        val bytes = svgContent.toByteArray(Charsets.UTF_8)
        val key = "diagrams/$diagramId/preview.svg"
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(key)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType("image/svg+xml")
                .build()
        )
        return true
    }

    override fun getSvg(diagramId: UUID): ByteArray? {
        val key = "diagrams/$diagramId/preview.svg"
        return try {
            val stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minioProperties.bucket)
                    .`object`(key)
                    .build()
            )
            stream.use { it.readAllBytes() }
        } catch (_: Exception) {
            null
        }
    }
}
