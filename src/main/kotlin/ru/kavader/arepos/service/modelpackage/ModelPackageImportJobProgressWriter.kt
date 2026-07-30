package ru.kavader.arepos.service.modelpackage

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.repository.PackageImportJobsRepository
import java.time.Instant
import java.util.UUID

@Service
class ModelPackageImportJobProgressWriter(
    private val jobsRepository: PackageImportJobsRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markRunning(jobId: UUID, stage: String, progress: Int, message: String?) {
        val job = jobsRepository.findById(jobId).orElse(null) ?: return
        if (job.status == PackageImportStages.STATUS_SUCCEEDED ||
            job.status == PackageImportStages.STATUS_FAILED
        ) {
            return
        }
        job.status = PackageImportStages.STATUS_RUNNING
        job.stage = stage
        job.progress = progress.coerceIn(0, 100)
        job.message = message
        job.updatedAt = Instant.now()
        jobsRepository.save(job)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateProgress(jobId: UUID, stage: String, progress: Int, message: String?) {
        val job = jobsRepository.findById(jobId).orElse(null) ?: return
        if (job.status == PackageImportStages.STATUS_SUCCEEDED ||
            job.status == PackageImportStages.STATUS_FAILED
        ) {
            return
        }
        if (job.status == PackageImportStages.STATUS_QUEUED) {
            job.status = PackageImportStages.STATUS_RUNNING
        }
        job.stage = stage
        job.progress = progress.coerceIn(0, 100)
        job.message = message
        job.updatedAt = Instant.now()
        jobsRepository.save(job)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markSucceeded(jobId: UUID, resultJson: String) {
        val job = jobsRepository.findById(jobId).orElse(null) ?: return
        val now = Instant.now()
        job.status = PackageImportStages.STATUS_SUCCEEDED
        job.stage = PackageImportStages.DONE
        job.progress = 100
        job.message = null
        job.resultJson = resultJson
        job.errorJson = null
        job.tempPath = null
        job.updatedAt = now
        job.finishedAt = now
        jobsRepository.save(job)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(jobId: UUID, errorJson: String, message: String?) {
        val job = jobsRepository.findById(jobId).orElse(null) ?: return
        val now = Instant.now()
        job.status = PackageImportStages.STATUS_FAILED
        job.message = message
        job.errorJson = errorJson
        job.resultJson = null
        job.tempPath = null
        job.updatedAt = now
        job.finishedAt = now
        jobsRepository.save(job)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markTimedOut(jobId: UUID, errorJson: String) {
        val job = jobsRepository.findById(jobId).orElse(null) ?: return
        if (job.status != PackageImportStages.STATUS_RUNNING &&
            job.status != PackageImportStages.STATUS_QUEUED
        ) {
            return
        }
        val now = Instant.now()
        job.status = PackageImportStages.STATUS_FAILED
        job.message = "Import job timed out"
        job.errorJson = errorJson
        job.tempPath = null
        job.updatedAt = now
        job.finishedAt = now
        jobsRepository.save(job)
    }
}
