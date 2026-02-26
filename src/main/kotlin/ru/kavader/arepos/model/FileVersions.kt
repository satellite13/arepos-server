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
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    val file: Files,

    @Column(name = "version_id", nullable = false)
    val versionId: String,

    @Column(name = "version_number", nullable = false)
    val versionNumber: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    val createdBy: Users,

    @Column(name = "size", nullable = false)
    val size: Long
)
