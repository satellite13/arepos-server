package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "document_refs", schema = "public")
data class DocumentRefs(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    val file: Files,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    val createdBy: Users,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_type_id")
    val nodeType: NodeTypes? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_type_id")
    val linkType: LinkTypes? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notation_id")
    val notation: Notations? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    val component: Components? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    val model: Models? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id")
    val node: Nodes? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id")
    val diagram: Diagrams? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relation_id")
    val relation: Relations? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_shape_id")
    val nodeShape: NodeShapes? = null,
)
