package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "comment_link_previews", schema = "public")
class CommentLinkPreview(
    @Id
    @Column(name = "url", updatable = false, nullable = false, length = 2048)
    var url: String,

    @Column(name = "title")
    var title: String? = null,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "site_name")
    var siteName: String? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_file_id")
    var imageFile: Files? = null,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant? = null
)
