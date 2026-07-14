package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "roadmap_milestones",
    schema = "public",
    indexes = [
        Index(name = "roadmap_milestones_sort_order_idx", columnList = "sort_order")
    ]
)
class RoadmapMilestone(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String = "",

    @Column(name = "status", nullable = false, length = 32)
    var status: String = "planned",

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "target_period", length = 64)
    var targetPeriod: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
)
