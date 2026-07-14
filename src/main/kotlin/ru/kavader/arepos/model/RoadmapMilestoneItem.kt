package ru.kavader.arepos.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "roadmap_milestone_items",
    schema = "public",
    uniqueConstraints = [
        UniqueConstraint(
            name = "roadmap_milestone_items_uq",
            columnNames = ["milestone_id", "feedback_item_id"]
        )
    ],
    indexes = [
        Index(name = "roadmap_milestone_items_feedback_idx", columnList = "feedback_item_id")
    ]
)
class RoadmapMilestoneItem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    var milestone: RoadmapMilestone,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_item_id", nullable = false)
    var feedbackItem: FeedbackItem
)
