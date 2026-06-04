package ru.kavader.arepos.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

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
class Links(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "stable_id", columnDefinition = "uuid", nullable = false)
    var stableId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source", nullable = false)
    var source: Nodes,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target", nullable = false)
    var target: Nodes,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    var attrs: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    var owner: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_type", nullable = false)
    var linkType: LinkTypes,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model", nullable = false)
    var model: Models
)
