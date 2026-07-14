package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "feedback_comments",
    schema = "public",
    indexes = [
        Index(name = "feedback_comments_item_id_idx", columnList = "item_id"),
        Index(name = "feedback_comments_author_id_idx", columnList = "author_id")
    ]
)
class FeedbackComment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    var item: FeedbackItem,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: Users,

    @Column(name = "body", nullable = false, columnDefinition = "text")
    var body: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
)
