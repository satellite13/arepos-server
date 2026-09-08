package ru.kavader.arepos.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "comment_attachments", schema = "public")
class DiagramCommentAttachment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    var comment: DiagramComment,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    var file: Files,

    @Column(name = "position", nullable = false)
    var position: Int = 0
)
