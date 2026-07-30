package ru.kavader.arepos.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.kavader.arepos.model.PackageImportJobs
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface PackageImportJobsRepository : JpaRepository<PackageImportJobs, UUID> {
    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Optional<PackageImportJobs>

    @Query(
        """
        SELECT j FROM PackageImportJobs j
        WHERE j.status IN ('SUCCEEDED', 'FAILED')
          AND j.finishedAt IS NOT NULL
          AND j.finishedAt < :before
        """
    )
    fun findFinishedBefore(before: Instant): List<PackageImportJobs>

    @Query(
        """
        SELECT j FROM PackageImportJobs j
        WHERE j.status IN ('QUEUED', 'RUNNING')
          AND j.updatedAt < :before
        """
    )
    fun findStaleRunning(before: Instant): List<PackageImportJobs>
}
