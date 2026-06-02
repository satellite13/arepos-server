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
@Table(name = "components", schema = "public", uniqueConstraints = [
    UniqueConstraint(name = "components_notation_name_version_key", columnNames = ["notation", "name", "version"])
], indexes = [
    Index(name = "components_notation_idx", columnList = "notation"),
    Index(name = "components_owner_idx", columnList = "owner"),
    Index(name = "components_node_type_idx", columnList = "node_type")
])
@JsonIgnoreProperties(ignoreUnknown = true)
data class Components(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "name", nullable = false)
    val name: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    val attrs: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,

    @Column(name = "version", nullable = false, columnDefinition = "version_type")
    val version: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notation", nullable = false)
    val notation: Notations,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    val owner: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_type", nullable = false)
    val nodeType: NodeTypes
)
