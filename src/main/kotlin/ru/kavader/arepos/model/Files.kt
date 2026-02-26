package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "files", schema = "public")
data class Files(
    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: Users,

    @Column(name = "filename", nullable = false)
    val filename: String,

    @Column(name = "content_type", nullable = false)
    val contentType: String,

    @Column(name = "size", nullable = false)
    val size: Long,

    @Column(name = "object_key", nullable = false)
    val objectKey: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null
)
