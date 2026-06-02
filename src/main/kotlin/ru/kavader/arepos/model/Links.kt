package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import java.time.Instant
import java.util.*
import org.hibernate.type.SqlTypes

@Entity
@DynamicUpdate
@BatchSize(size = 50)
@Table(
    name = "links",
    schema = "public",
    indexes = [
        Index(name = "links_source_idx", columnList = "source"),
        Index(name = "links_target_idx", columnList = "target"),
        Index(name = "links_owner_idx", columnList = "owner"),
        Index(name = "links_link_type_idx", columnList = "link_type"),
        Index(name = "links_model_idx", columnList = "model")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Links(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "stable_id", columnDefinition = "uuid", nullable = false)
    val stableId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source", nullable = false)
    val source: Nodes,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target", nullable = false)
    val target: Nodes,

    @JdbcTypeCode(SqlTypes.JSON)
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
