package ru.kavader.arepos.service

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.errors.ErrorResponseException
import io.minio.errors.MinioException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import ru.kavader.arepos.config.MinioProperties
import java.io.IOException
import java.io.ByteArrayInputStream
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["arepos.files.storage"], havingValue = "minio")
class MinioDiagramSvgStorage(
    private val minioClient: MinioClient,
    private val minioProperties: MinioProperties
) : DiagramSvgStorage {
    companion object {
        private val log = LoggerFactory.getLogger(MinioDiagramSvgStorage::class.java)
    }

    override fun putSvg(diagramId: UUID, svgContent: String): DiagramSvgWriteResult {
        val bytes = svgContent.toByteArray(Charsets.UTF_8)
        val key = "diagrams/$diagramId/preview.svg"
        return try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioProperties.bucket)
                    .`object`(key)
                    .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                    .contentType("application/octet-stream")
                    .build()
            )
            DiagramSvgWriteResult.Written
        } catch (ex: ErrorResponseException) {
            val code = ex.errorResponse().code()
            log.error("MinIO error while writing diagram preview {}: {}", diagramId, code, ex)
            DiagramSvgWriteResult.StorageError(code)
        } catch (ex: IOException) {
            log.error("I/O error while writing diagram preview {}", diagramId, ex)
            DiagramSvgWriteResult.StorageError(ex.message)
        } catch (ex: MinioException) {
            log.error("MinIO exception while writing diagram preview {}", diagramId, ex)
            DiagramSvgWriteResult.StorageError(ex.message)
        } catch (ex: Exception) {
            log.error("Unexpected storage error while writing diagram preview {}", diagramId, ex)
            DiagramSvgWriteResult.StorageError(ex.message)
        }
    }

    override fun getSvg(diagramId: UUID): DiagramSvgReadResult {
        val key = "diagrams/$diagramId/preview.svg"
        return try {
            val stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minioProperties.bucket)
                    .`object`(key)
                    .build()
            )
            DiagramSvgReadResult.Found(stream.use { it.readAllBytes() })
        } catch (ex: ErrorResponseException) {
            val code = ex.errorResponse().code()
            if (code == "NoSuchKey" || code == "NoSuchVersion") {
                DiagramSvgReadResult.NotFound
            } else {
                log.error("MinIO error while reading diagram preview {}: {}", diagramId, code, ex)
                DiagramSvgReadResult.StorageError(code)
            }
        } catch (ex: IOException) {
            log.error("I/O error while reading diagram preview {}", diagramId, ex)
            DiagramSvgReadResult.StorageError(ex.message)
        } catch (ex: MinioException) {
            log.error("MinIO exception while reading diagram preview {}", diagramId, ex)
            DiagramSvgReadResult.StorageError(ex.message)
        } catch (ex: Exception) {
            log.error("Unexpected storage error while reading diagram preview {}", diagramId, ex)
            DiagramSvgReadResult.StorageError(ex.message)
        }
    }
}
