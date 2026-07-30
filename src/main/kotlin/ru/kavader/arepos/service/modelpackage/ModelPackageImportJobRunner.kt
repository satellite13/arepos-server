package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.config.AuditInterceptor
import ru.kavader.arepos.dto.modelpackage.PackageImportJobErrorDto
import ru.kavader.arepos.dto.modelpackage.PackageImportJobResultDto
import ru.kavader.arepos.repository.UsersRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
class ModelPackageImportJobRunner(
    private val jobAccessor: ModelPackageImportJobAccessor,
    private val usersRepository: UsersRepository,
    private val importService: ModelPackageImportService,
    private val progressWriter: ModelPackageImportJobProgressWriter,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async("packageImportExecutor")
    fun runAsync(jobId: UUID) {
        val snapshot = jobAccessor.loadSnapshot(jobId)
        if (snapshot == null) {
            logger.warn("Package import job {} not found or incomplete", jobId)
            return
        }

        AuditInterceptor.setCurrentUserId(snapshot.ownerId)
        try {
            progressWriter.markRunning(
                jobId = jobId,
                stage = PackageImportStages.VALIDATING,
                progress = 5,
                message = "Validating package"
            )

            val owner = usersRepository.findById(snapshot.ownerId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "User ${snapshot.ownerId} not found")
            }
            val zipBytes = Files.readAllBytes(Path.of(snapshot.tempPath))
            val listener = PackageImportProgressListener { stage, progress, message ->
                progressWriter.updateProgress(jobId, stage, progress, message)
            }
            val result = importService.importPackage(zipBytes, owner, listener)
            val resultJson = objectMapper.writeValueAsString(
                PackageImportJobResultDto(
                    modelId = result.modelId,
                    modelName = result.modelName,
                    modelVersion = result.modelVersion,
                    warnings = result.warnings
                )
            )
            progressWriter.markSucceeded(jobId, resultJson)
        } catch (ex: ResponseStatusException) {
            val status = ex.statusCode.value()
            val message = ex.reason ?: ex.message ?: "Import failed"
            val code = when (status) {
                HttpStatus.CONFLICT.value() -> "CONFLICT"
                HttpStatus.PAYLOAD_TOO_LARGE.value() -> "PAYLOAD_TOO_LARGE"
                HttpStatus.BAD_REQUEST.value() -> "BAD_REQUEST"
                else -> null
            }
            progressWriter.markFailed(
                jobId,
                objectMapper.writeValueAsString(
                    PackageImportJobErrorDto(status = status, message = message, code = code)
                ),
                message
            )
        } catch (ex: Exception) {
            logger.error("Package import job {} failed", jobId, ex)
            val message = ex.message?.takeIf { it.isNotBlank() } ?: "Import failed"
            progressWriter.markFailed(
                jobId,
                objectMapper.writeValueAsString(
                    PackageImportJobErrorDto(status = 500, message = message)
                ),
                message
            )
        } finally {
            ModelPackageImportJobService.deleteTempQuietly(snapshot.tempPath)
            AuditInterceptor.clearCurrentUserId()
        }
    }
}
