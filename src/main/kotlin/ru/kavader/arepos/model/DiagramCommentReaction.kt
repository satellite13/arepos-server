package ru.kavader.arepos.model

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "comment_reactions", schema = "public")
class DiagramCommentReaction(
    @EmbeddedId
    var key: DiagramCommentReactionKey,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("commentId")
    @JoinColumn(name = "comment_id", nullable = false)
    var comment: DiagramComment,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    var user: Users,

    @Column(name = "emoji", nullable = false, insertable = false, updatable = false)
    var emoji: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
)

@Embeddable
class DiagramCommentReactionKey(
    @Column(name = "comment_id", nullable = false)
    var commentId: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null,

    @Column(name = "emoji", nullable = false)
    var emoji: String = ""
) : Serializable
