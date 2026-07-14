package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "feedback_items",
    schema = "public",
    indexes = [
        Index(name = "feedback_items_author_id_idx", columnList = "author_id"),
        Index(name = "feedback_items_status_idx", columnList = "status"),
        Index(name = "feedback_items_type_idx", columnList = "type"),
        Index(name = "feedback_items_vote_count_idx", columnList = "vote_count"),
        Index(name = "feedback_items_created_at_idx", columnList = "created_at")
    ]
)
class FeedbackItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "type", nullable = false, length = 16)
    var type: String,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "body", nullable = false, columnDefinition = "text")
    var body: String,

    @Column(name = "status", nullable = false, length = 32)
    var status: String = "new",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: Users,

    @Column(name = "vote_count", nullable = false)
    var voteCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
)
