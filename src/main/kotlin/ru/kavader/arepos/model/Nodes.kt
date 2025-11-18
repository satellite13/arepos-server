package ru.kavader.arepos.model

import jakarta.persistence.*
import java.time.Instant
import java.util.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity
@Table(name = "nodes", schema = "public")
@JsonIgnoreProperties(ignoreUnknown = true)
data class Nodes(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID? = null,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,

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
