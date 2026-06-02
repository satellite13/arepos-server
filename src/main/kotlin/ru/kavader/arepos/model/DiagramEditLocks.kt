package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "diagram_edit_locks",
    schema = "public",
    indexes = [
        Index(name = "diagram_edit_locks_locked_by_user_id_idx", columnList = "locked_by_user_id")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class DiagramEditLocks(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false, unique = true)
    val diagram: Diagrams,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locked_by_user_id", nullable = false)
    var lockedBy: Users,

    @Column(name = "locked_at", nullable = false)
    var lockedAt: Instant,

    @Column(name = "last_heartbeat_at", nullable = false)
    var lastHeartbeatAt: Instant,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)
