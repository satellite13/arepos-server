package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "download_assets",
    schema = "public",
    indexes = [
        Index(name = "download_assets_published_idx", columnList = "published"),
        Index(name = "download_assets_sort_order_idx", columnList = "sort_order"),
        Index(name = "download_assets_file_id_idx", columnList = "file_id")
    ]
)
class DownloadAsset(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String = "",

    @Column(name = "kind", nullable = false, length = 32)
    var kind: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    var file: Files,

    @Column(name = "file_name", nullable = false)
    var fileName: String,

    @Column(name = "content_type", nullable = false)
    var contentType: String,

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,

    @Column(name = "version_label", length = 64)
    var versionLabel: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "published", nullable = false)
    var published: Boolean = true,

    @Column(name = "download_count", nullable = false)
    var downloadCount: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
)
