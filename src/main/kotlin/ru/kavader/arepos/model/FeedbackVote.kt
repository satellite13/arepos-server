package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "feedback_votes",
    schema = "public",
    uniqueConstraints = [
        UniqueConstraint(name = "feedback_votes_item_user_uq", columnNames = ["item_id", "user_id"])
    ],
    indexes = [
        Index(name = "feedback_votes_user_id_idx", columnList = "user_id")
    ]
)
class FeedbackVote(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    var item: FeedbackItem,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: Users,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
)
