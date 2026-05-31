package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "files", schema = "public")
data class Files(
    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: Users,

    @Column(name = "filename", nullable = false)
    var filename: String,

    @Column(name = "content_type", nullable = false)
    var contentType: String,

    @Column(name = "size", nullable = false)
    var size: Long,

    @Column(name = "object_key", nullable = false)
    var objectKey: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
)
