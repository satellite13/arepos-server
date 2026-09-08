package ru.kavader.arepos.model

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "comment_read_state", schema = "public")
class DiagramCommentReadState(
    @EmbeddedId
    var key: DiagramCommentReadStateKey,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    var user: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("diagramId")
    @JoinColumn(name = "diagram_id", nullable = false)
    var diagram: Diagrams,

    @Column(name = "last_read_at", nullable = false)
    var lastReadAt: Instant? = null
)

@Embeddable
class DiagramCommentReadStateKey(
    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null,

    @Column(name = "diagram_id", nullable = false)
    var diagramId: UUID? = null
) : Serializable
