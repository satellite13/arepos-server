package ru.kavader.arepos.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "notifications",
    schema = "public",
    indexes = [
        Index(name = "notifications_user_idx", columnList = "user_id, created_at DESC")
    ]
)
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: Users,

    /** 'comment_reply' | 'comment_mention' */
    @Column(name = "type", nullable = false, length = 32)
    var type: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    var payload: String? = null,

    @Column(name = "read_at")
    var readAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
)
