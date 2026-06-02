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
data class Nodes(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "stable_id", columnDefinition = "uuid", nullable = false)
    val stableId: UUID,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    val attrs: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_node")
    val parentNode: Nodes? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model", nullable = false)
    val model: Models,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    val owner: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_type", nullable = false)
    val nodeType: NodeTypes
)
