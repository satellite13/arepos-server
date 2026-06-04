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
    name = "nodes",
    schema = "public",
    indexes = [
        Index(name = "nodes_parent_node_idx", columnList = "parent_node"),
        Index(name = "nodes_model_idx", columnList = "model"),
        Index(name = "nodes_owner_idx", columnList = "owner"),
        Index(name = "nodes_node_type_idx", columnList = "node_type")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class Nodes(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "stable_id", columnDefinition = "uuid", nullable = false)
    var stableId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    var attrs: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_node")
    var parentNode: Nodes? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model", nullable = false)
    var model: Models,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    var owner: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_type", nullable = false)
    var nodeType: NodeTypes
)
