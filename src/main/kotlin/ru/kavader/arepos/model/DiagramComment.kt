package ru.kavader.arepos.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "comments",
    schema = "public",
    indexes = [
        Index(name = "comments_diagram_created_idx", columnList = "diagram_id, created_at"),
        Index(name = "comments_diagram_target_idx", columnList = "diagram_id, target_type, instance_id"),
        Index(name = "comments_thread_idx", columnList = "thread_id")
    ]
)
class DiagramComment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false, insertable = false, updatable = false)
    var model: Models? = null,

    @Column(name = "model_id", nullable = false)
    var modelId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", nullable = false)
    var diagram: Diagrams,

    /** 'diagram' | 'node' | 'edge' */
    @Column(name = "target_type", nullable = false, length = 16)
    var targetType: String,

    /** Canvas instance id (DiagramNodeInstance.id / DiagramEdgeInstance.id); null for diagram-level. */
    @Column(name = "instance_id")
    var instanceId: String? = null,

    /** Snapshot of the element display name at comment time. */
    @Column(name = "element_name")
    var elementName: String? = null,

    /** Root comment of the thread; null when this comment is the root itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id")
    var thread: DiagramComment? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: Users,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mentions", columnDefinition = "jsonb", nullable = false)
    var mentions: String? = null,

    @Column(name = "body_md", nullable = false, columnDefinition = "text")
    var bodyMd: String,

    @Column(name = "is_resolved", nullable = false)
    var isResolved: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    var resolvedBy: Users? = null,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    /** 'manual' | 'element_removed' */
    @Column(name = "resolved_reason", length = 32)
    var resolvedReason: String? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "edited_at")
    var editedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null
)
