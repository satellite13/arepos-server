package ru.kavader.arepos.service.modelpackage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.modelpackage.ModelPackageImportOverrides
import ru.kavader.arepos.dto.modelpackage.PackageImportErrorCodes
import ru.kavader.arepos.dto.modelpackage.PackageImportJobAcceptedResponse
import ru.kavader.arepos.dto.modelpackage.PackageImportJobErrorDto
import ru.kavader.arepos.dto.modelpackage.PackageImportJobResultDto
import ru.kavader.arepos.dto.modelpackage.PackageImportJobRetryRequest
import ru.kavader.arepos.dto.modelpackage.PackageImportJobStatusResponse
import ru.kavader.arepos.model.PackageImportJobs
import ru.kavader.arepos.model.Users
import ru.kavader.arepos.repository.PackageImportJobsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

@Service
class ModelPackageImportJobService(
    private val jobsRepository: PackageImportJobsRepository,
    private val jobRunner: ModelPackageImportJobRunner,
    private val progressWriter: ModelPackageImportJobProgressWriter,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun acceptUpload(file: MultipartFile, owner: Users): PackageImportJobAcceptedResponse {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Package file is required")
        }
        if (file.size > ModelPackageLimits.MAX_ZIP_BYTES) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Package exceeds ${ModelPackageLimits.MAX_ZIP_BYTES} bytes limit"
            )
        }

        val tempPath = Files.createTempFile("arepos-package-import-", ".zip")
        try {
            file.inputStream.use { input ->
                Files.newOutputStream(tempPath).use { output ->
                    input.transferTo(output)
                }
            }
        } catch (ex: Exception) {
            Files.deleteIfExists(tempPath)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to store package upload", ex)
        }

        val now = Instant.now()
        val job = jobsRepository.save(
            PackageImportJobs(
                owner = owner,
                status = PackageImportStages.STATUS_QUEUED,
                stage = PackageImportStages.QUEUED,
                progress = 0,
                message = "Queued",
                tempPath = tempPath.toAbsolutePath().toString(),
                createdAt = now,
                updatedAt = now
            )
        )
        val jobId = job.id!!
        // Start worker only after the job row is committed (async thread must see it).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        jobRunner.runAsync(jobId)
                    }
                }
            )
        } else {
            jobRunner.runAsync(jobId)
        }
        return PackageImportJobAcceptedResponse(
            jobId = jobId,
            status = PackageImportStages.STATUS_QUEUED
        )
    }

    @Transactional(readOnly = true)
    fun getJob(jobId: UUID, ownerId: UUID): PackageImportJobStatusResponse {
        val job = jobsRepository.findByIdAndOwnerId(jobId, ownerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Import job $jobId not found")
        }
        return toStatusResponse(job)
    }

    @Transactional
    fun retryJob(
        jobId: UUID,
        ownerId: UUID,
        request: PackageImportJobRetryRequest
    ): PackageImportJobAcceptedResponse {
        val job = jobsRepository.findByIdAndOwnerId(jobId, ownerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Import job $jobId not found")
        }
        if (job.status != PackageImportStages.STATUS_FAILED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Import job $jobId is not retryable")
        }
        val error = job.errorJson?.let { json ->
            try {
                objectMapper.readValue<PackageImportJobErrorDto>(json)
            } catch (_: Exception) {
                null
            }
        }
        if (error?.code != PackageImportErrorCodes.MODEL_EXISTS) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Import job $jobId can only be retried after MODEL_EXISTS"
            )
        }
        val tempPath = job.tempPath
        if (tempPath.isNullOrBlank() || !Files.exists(Path.of(tempPath))) {
            throw ResponseStatusException(
                HttpStatus.GONE,
                "Import job $jobId package upload is no longer available; upload the package again"
            )
        }

        val name = request.targetModelName?.trim()?.takeIf { it.isNotEmpty() }
        val version = request.targetModelVersion?.trim()?.takeIf { it.isNotEmpty() }
        if (name == null && version == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide targetModelName and/or targetModelVersion"
            )
        }

        val overridesJson = objectMapper.writeValueAsString(
            ModelPackageImportOverrides(
                targetModelName = name,
                targetModelVersion = version
            )
        )
        val requeued = progressWriter.requeueForRetry(jobId, overridesJson)
        if (!requeued) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Import job $jobId is not retryable")
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        jobRunner.runAsync(jobId)
                    }
                }
            )
        } else {
            jobRunner.runAsync(jobId)
        }

        return PackageImportJobAcceptedResponse(
            jobId = jobId,
            status = PackageImportStages.STATUS_QUEUED
        )
    }

    @Transactional
    fun cleanupFinishedJobs(retention: java.time.Duration): Int {
        val before = Instant.now().minus(retention)
        val jobs = jobsRepository.findFinishedBefore(before)
        var deleted = 0
        for (job in jobs) {
            deleteTempQuietly(job.tempPath)
            jobsRepository.delete(job)
            deleted++
        }
        return deleted
    }

    @Transactional
    fun failStaleRunningJobs(maxAge: java.time.Duration): Int {
        val before = Instant.now().minus(maxAge)
        val stale = jobsRepository.findStaleRunning(before)
        var failed = 0
        val errorJson = objectMapper.writeValueAsString(
            PackageImportJobErrorDto(
                status = HttpStatus.GATEWAY_TIMEOUT.value(),
                message = "Import job timed out",
                code = "TIMEOUT"
            )
        )
        for (job in stale) {
            val jobId = job.id ?: continue
            val tempPath = job.tempPath
            progressWriter.markTimedOut(jobId, errorJson)
            deleteTempQuietly(tempPath)
            failed++
        }
        return failed
    }

    private fun toStatusResponse(job: PackageImportJobs): PackageImportJobStatusResponse {
        val result = job.resultJson?.let { json ->
            try {
                objectMapper.readValue<PackageImportJobResultDto>(json)
            } catch (ex: Exception) {
                logger.warn("Failed to parse import job result for {}", job.id, ex)
                null
            }
        }
        val error = job.errorJson?.let { json ->
            try {
                objectMapper.readValue<PackageImportJobErrorDto>(json)
            } catch (ex: Exception) {
                logger.warn("Failed to parse import job error for {}", job.id, ex)
                null
            }
        }
        return PackageImportJobStatusResponse(
            jobId = job.id!!,
            status = job.status,
            stage = job.stage,
            progress = job.progress,
            message = job.message,
            result = result,
            error = error
        )
    }

    companion object {
        fun deleteTempQuietly(tempPath: String?) {
            if (tempPath.isNullOrBlank()) return
            try {
                Files.deleteIfExists(Path.of(tempPath))
            } catch (_: Exception) {
                // best-effort cleanup
            }
        }
    }
}
