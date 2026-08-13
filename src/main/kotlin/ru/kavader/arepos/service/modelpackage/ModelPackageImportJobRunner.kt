package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.config.AuditInterceptor
import ru.kavader.arepos.dto.modelpackage.ModelPackageImportOverrides
import ru.kavader.arepos.dto.modelpackage.PackageImportErrorCodes
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

        val owner = usersRepository.findById(snapshot.ownerId).orElse(null)
        if (owner == null) {
            logger.warn("Package import job {} owner {} not found", jobId, snapshot.ownerId)
            progressWriter.markFailed(
                jobId,
                objectMapper.writeValueAsString(
                    PackageImportJobErrorDto(status = 404, message = "User ${snapshot.ownerId} not found")
                ),
                "User ${snapshot.ownerId} not found",
                keepTemp = false
            )
            ModelPackageImportJobService.deleteTempQuietly(snapshot.tempPath)
            return
        }

        AuditInterceptor.setCurrentUserId(snapshot.ownerId)
        val previousAuth = SecurityContextHolder.getContext().authentication
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            snapshot.ownerId,
            null,
            listOf(SimpleGrantedAuthority("ROLE_${owner.role.name}"))
        )

        var keepTemp = false
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
            val overrides = snapshot.overridesJson?.let { json ->
                try {
                    objectMapper.readValue<ModelPackageImportOverrides>(json)
                } catch (ex: Exception) {
                    logger.warn("Package import job {} invalid overrides_json", jobId, ex)
                    null
                }
            }
            val result = importService.importPackage(zipBytes, owner, listener, overrides)
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
        } catch (ex: PackageImportConflictException) {
            keepTemp = ex.code == PackageImportErrorCodes.MODEL_EXISTS
            val status = ex.statusCode.value()
            val message = ex.reason ?: ex.message ?: "Import failed"
            logger.error(
                "Package import job {} failed with conflict {}: {} (keepTemp={})",
                jobId,
                ex.code,
                message,
                keepTemp,
                ex
            )
            progressWriter.markFailed(
                jobId,
                objectMapper.writeValueAsString(
                    PackageImportJobErrorDto(
                        status = status,
                        message = message,
                        code = ex.code,
                        conflict = ex.conflict
                    )
                ),
                message,
                keepTemp = keepTemp
            )
        } catch (ex: ResponseStatusException) {
            val status = ex.statusCode.value()
            val message = ex.reason ?: ex.message ?: "Import failed"
            val code = when (status) {
                HttpStatus.CONFLICT.value() -> PackageImportErrorCodes.CONFLICT
                HttpStatus.PAYLOAD_TOO_LARGE.value() -> PackageImportErrorCodes.PAYLOAD_TOO_LARGE
                HttpStatus.BAD_REQUEST.value() -> PackageImportErrorCodes.BAD_REQUEST
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
                message,
                keepTemp = false
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
                    message,
                    keepTemp = false
                )
            }.onFailure { markEx ->
                logger.error("Package import job {} could not mark failed after fatal error", jobId, markEx)
            }
            if (ex is Error && ex !is OutOfMemoryError) {
                throw ex
            }
        } finally {
            if (!keepTemp) {
                ModelPackageImportJobService.deleteTempQuietly(snapshot.tempPath)
            }
            SecurityContextHolder.getContext().authentication = previousAuth
            AuditInterceptor.clearCurrentUserId()
            logger.info("Package import job {} finished cleanup (keepTemp={})", jobId, keepTemp)
        }
    }
}
