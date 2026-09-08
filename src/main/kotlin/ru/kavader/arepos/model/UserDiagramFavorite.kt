package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "user_diagram_favorites",
    schema = "public",
    uniqueConstraints = [
        UniqueConstraint(name = "user_diagram_favorites_user_diagram_uq", columnNames = ["user_id", "diagram_id"])
    ],
    indexes = [
        Index(name = "user_diagram_favorites_user_id_idx", columnList = "user_id"),
        Index(name = "user_diagram_favorites_diagram_id_idx", columnList = "diagram_id")
    ]
)
class UserDiagramFavorite(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id", nullable = false)
    var diagram: Diagrams,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null
)
