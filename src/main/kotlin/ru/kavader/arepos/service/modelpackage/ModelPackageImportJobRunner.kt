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
import ru.kavader.arepos.service.formatMinioFailure
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
        logger.info(
            "Package import job {} started (ownerId={}, tempPath={}, thread={})",
            jobId,
            snapshot.ownerId,
            snapshot.tempPath,
            Thread.currentThread().name
        )
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
            logger.info("Package import job {} loaded zip ({} bytes)", jobId, zipBytes.size)
            val listener = PackageImportProgressListener { stage, progress, message ->
                logger.info(
                    "Package import job {} progress: stage={}, progress={}, message={}",
                    jobId,
                    stage,
                    progress,
                    message
                )
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
            logger.info(
                "Package import job {} succeeded (modelId={}, warnings={})",
                jobId,
                result.modelId,
                result.warnings.size
            )
        } catch (ex: ResponseStatusException) {
            val status = ex.statusCode.value()
            val message = ex.reason ?: ex.message ?: "Import failed"
            val code = when (status) {
                HttpStatus.CONFLICT.value() -> "CONFLICT"
                HttpStatus.PAYLOAD_TOO_LARGE.value() -> "PAYLOAD_TOO_LARGE"
                HttpStatus.BAD_REQUEST.value() -> "BAD_REQUEST"
                else -> null
            }
            logger.error(
                "Package import job {} failed with status {}: {} (cause={})",
                jobId,
                status,
                message,
                ex.cause?.let { formatMinioFailure(it) } ?: ex.message,
                ex
            )
            progressWriter.markFailed(
                jobId,
                objectMapper.writeValueAsString(
                    PackageImportJobErrorDto(status = status, message = message, code = code)
                ),
                message
            )
        } catch (ex: Throwable) {
            // Catch Error too (OOM/StackOverflow): otherwise job stays RUNNING forever.
            logger.error("Package import job {} failed", jobId, ex)
            val message = ex.message?.takeIf { it.isNotBlank() } ?: ex.javaClass.simpleName
            runCatching {
                progressWriter.markFailed(
                    jobId,
                    objectMapper.writeValueAsString(
                        PackageImportJobErrorDto(status = 500, message = message)
                    ),
                    message
                )
            }.onFailure { markEx ->
                logger.error("Package import job {} could not mark failed after fatal error", jobId, markEx)
            }
            if (ex is Error && ex !is OutOfMemoryError) {
                throw ex
            }
        } finally {
            ModelPackageImportJobService.deleteTempQuietly(snapshot.tempPath)
            AuditInterceptor.clearCurrentUserId()
            logger.info("Package import job {} finished cleanup", jobId)
        }
    }
}
