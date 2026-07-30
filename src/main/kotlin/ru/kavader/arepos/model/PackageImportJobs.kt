package ru.kavader.arepos.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "package_import_jobs", schema = "public")
class PackageImportJobs(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: Users,

    @Column(name = "status", nullable = false, length = 32)
    var status: String,

    @Column(name = "stage", nullable = false, length = 64)
    var stage: String,

    @Column(name = "progress", nullable = false)
    var progress: Int = 0,

    @Column(name = "message", columnDefinition = "text")
    var message: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    var resultJson: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_json", columnDefinition = "jsonb")
    var errorJson: String? = null,

    @Column(name = "temp_path", columnDefinition = "text")
    var tempPath: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null
)
