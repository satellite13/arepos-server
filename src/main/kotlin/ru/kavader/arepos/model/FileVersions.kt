package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "file_versions", schema = "public")
data class FileVersions(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    var file: Files,

    @Column(name = "version_id", nullable = false)
    var versionId: String,

    @Column(name = "version_number", nullable = false)
    var versionNumber: Int,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    var createdBy: Users,

    @Column(name = "size", nullable = false)
    var size: Long
)
