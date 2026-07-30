package ru.kavader.arepos.service.modelpackage

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.kavader.arepos.repository.PackageImportJobsRepository
import java.util.UUID

@Service
class ModelPackageImportJobAccessor(
    private val jobsRepository: PackageImportJobsRepository
) {
    @Transactional(readOnly = true)
    fun loadSnapshot(jobId: UUID): PackageImportJobSnapshot? {
        val job = jobsRepository.findById(jobId).orElse(null) ?: return null
        val ownerId = job.owner.id ?: return null
        val path = job.tempPath ?: return null
        return PackageImportJobSnapshot(ownerId = ownerId, tempPath = path)
    }
}
