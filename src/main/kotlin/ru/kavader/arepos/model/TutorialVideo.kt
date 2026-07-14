package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "tutorial_videos",
    schema = "public",
    indexes = [
        Index(name = "tutorial_videos_sort_order_idx", columnList = "sort_order"),
        Index(name = "tutorial_videos_published_idx", columnList = "published")
    ]
)
class TutorialVideo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String = "",

    @Column(name = "provider", nullable = false, length = 32)
    var provider: String,

    @Column(name = "external_id", nullable = false, length = 128)
    var externalId: String,

    @Column(name = "embed_url", nullable = false, length = 512)
    var embedUrl: String,

    @Column(name = "thumbnail_url", length = 512)
    var thumbnailUrl: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "published", nullable = false)
    var published: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
)
