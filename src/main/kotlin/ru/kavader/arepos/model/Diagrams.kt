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
    name = "diagrams",
    schema = "public",
    uniqueConstraints = [
        UniqueConstraint(name = "diagrams_model_name_version_key", columnNames = ["model", "name", "version"])
    ],
    indexes = [
        Index(name = "diagrams_owner_idx", columnList = "owner"),
        Index(name = "diagrams_model_idx", columnList = "model"),
        Index(name = "diagrams_notation_id_idx", columnList = "notation_id"),
        Index(name = "diagrams_node_id_idx", columnList = "node_id")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class Diagrams(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    var attrs: String? = null,

    @Column(name = "version", nullable = false, columnDefinition = "version_type")
    var version: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    var owner: Users,

    @Column(name = "deleted", nullable = false)
    var deleted: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model", nullable = false)
    var model: Models,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notation_id", nullable = false)
    var notation: Notations,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id")
    var node: Nodes? = null
)
