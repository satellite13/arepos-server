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
    val id: UUID? = null,

    @Column(name = "token", columnDefinition = "uuid", nullable = false, unique = true)
    val token: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id")
    val diagram: Diagrams? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    val model: Models? = null,

    @Column(name = "diagram_name")
    val diagramName: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    val createdBy: Users? = null
)
