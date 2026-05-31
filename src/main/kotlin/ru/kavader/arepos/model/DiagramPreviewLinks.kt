package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "diagram_preview_links", schema = "public")
@JsonIgnoreProperties(ignoreUnknown = true)
data class DiagramPreviewLinks(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "token", columnDefinition = "uuid", nullable = false, unique = true)
    var token: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id")
    var diagram: Diagrams? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    var model: Models? = null,

    @Column(name = "diagram_name")
    var diagramName: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    var createdBy: Users? = null
)
