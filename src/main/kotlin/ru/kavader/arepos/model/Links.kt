package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity
@Table(name = "links", schema = "public")
@JsonIgnoreProperties(ignoreUnknown = true)
data class Links(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source", nullable = false)
    val source: Nodes,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target", nullable = false)
    val target: Nodes,

    @Column(name = "attrs", columnDefinition = "jsonb")
    val attrs: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    val owner: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_type", nullable = false)
    val linkType: LinkTypes,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model", nullable = false)
    val model: Models
)
