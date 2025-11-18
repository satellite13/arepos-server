package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity
@Table(name = "components", schema = "public", uniqueConstraints = [
    UniqueConstraint(name = "components_notation_name_version_key", columnNames = ["notation", "name", "version"])
])
@JsonIgnoreProperties(ignoreUnknown = true)
data class Components(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "name", nullable = false)
    val name: String,

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
