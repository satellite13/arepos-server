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
    name = "components", schema = "public", uniqueConstraints = [
        UniqueConstraint(name = "components_notation_name_version_key", columnNames = ["notation", "name", "version"])
    ], indexes = [
        Index(name = "components_notation_idx", columnList = "notation"),
        Index(name = "components_owner_idx", columnList = "owner"),
        Index(name = "components_node_type_idx", columnList = "node_type")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
class Components(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "name", nullable = false)
    override var name: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    override var attrs: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @Column(name = "version", nullable = false, columnDefinition = "version_type")
    override var version: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notation", nullable = false)
    override var notation: Notations,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    override var owner: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_type", nullable = false)
    var nodeType: NodeTypes
) : NotationBoundEntity
